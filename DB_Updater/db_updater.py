import cmd
import sys
from asyncio import sleep
from pymongo import MongoClient
from neo4j import GraphDatabase
import pandas as pd
import random
import time
from datetime import datetime, date, timedelta

import getPapers


class App(cmd.Cmd):
    intro = 'PaperRater DB_Updater launched. \n \nType help or ? to list commands.\n'
    prompt = '>'
    num_users = '1000'
    start_date = '2015-01-01'

    #mongo_client = MongoClient('172.16.4.68', 27020, username='admin', password='paperRaterApp', w=3, readPreference='secondaryPreferred')
    #neo4j_driver = GraphDatabase.driver("bolt://172.16.4.68:7687", auth=("neo4j", "paperRaterApp"))
    mongo_client = MongoClient('localhost', 27017)
    neo4j_driver = GraphDatabase.driver("bolt://localhost:7687", auth=("neo4j", "root"))


    def do_initDB(self, arg):
        'Initialize database'

        # Import data
        # papers_path = getPapers.import_data(self.start_date)
        #papers_path = './data/papers2015-01-01.json'
        papers_path = './data/papers2021-11-30.json'
        # users_path = getUsers.import_data(num_users)
        users_path = './data/users.json'

        # Initialization of utils
        session = self.neo4j_driver.session()
        users_df = pd.read_json(users_path, lines=True)
        papers_df = pd.read_json(papers_path, lines=True)

        # Drop old databases
        self.mongo_client.drop_database('PaperRater')
        query = ("MATCH (n) DETACH DELETE n")
        session.write_transaction(lambda tx: tx.run(query))

        # Create new databases
        db = self.mongo_client["PaperRater"]
        ### Neo4j database needs to be created

        ### Papers
        self.insert_papers(papers_df)

        # Converts IDs to str
        papers_df['arxiv_id'] = papers_df['arxiv_id'].map(str)
        papers_df['vixra_id'] = papers_df['vixra_id'].map(str)

        ### Users
        users_col = db["Users"]
        data_dict = users_df.to_dict("records")
        users_col.insert_many(data_dict)

        for index, row in users_df.iterrows():
            query = ("CREATE (u:User { username: $username , email: $email}) ")
            session.write_transaction(lambda tx: tx.run(query, username=row['username'], email=row['email']))

        print("Added users to database")
       
        ### Comments
        for index, row in papers_df.iterrows():
            num_comments = int(random.random() * 10)
            comments = []
            for i in range(0, num_comments):
                now = datetime.now()
                rand_user = users_df.sample()['username'].values[0]

                comment = {'username': rand_user,
                           'text': "Commento",  # getRandom Comment
                           'timestamp': now.strftime("%Y-%m-%d %H:%M:%S")}
                comments.append(comment)

            db.Papers.update_one({"$or":[{'arxiv_id': row['arxiv_id']}, {'vixra_id': row['vixra_id']}]}, {'$set': {'comments': comments}})

        print("Added comments to database")

        ### Reading Lists
        for index, row in users_df.iterrows():
            num_reading_lists = int(random.random() * 6)
            reading_lists = []

            # Generate a random number of reading lists
            for i in range(0, num_reading_lists):
                title = 'r_list' + str(i)
                reading_list = {'title': title}

                papers = []
                num_papers_in_reading_list = int(random.random() * 31)

                # Select a random number of papers to add to the reading list
                for j in range(0, num_papers_in_reading_list):
                    random_paper = papers_df.sample()
                    paper_to_add = {}
                    if random_paper['arxiv_id'].values[0] == "nan":
                        paper_to_add['vixra_id'] = random_paper['vixra_id'].values[0]
                    else:
                        paper_to_add['arxiv_id'] = random_paper['arxiv_id'].values[0]

                    paper_to_add['title'] = random_paper['title'].values[0]
                    paper_to_add['published'] = random_paper['published'].values[0]
                    paper_to_add['authors'] = random_paper['authors'].values[0]
                    paper_to_add['category'] = random_paper['category'].values[0]

                    papers.append(paper_to_add)

                reading_list['papers'] = papers
                reading_lists.append(reading_list)

                query = ("MATCH (a:User) "
                         "WHERE a.username = $username "
                         "CREATE (b:ReadingList { owner: $username, title: $title}) ")
                session.write_transaction(
                    lambda tx: tx.run(query, username=row['username'], title=reading_list['title']))

                n_follows = int(random.random() * 4)
                for k in range(0, n_follows):

                    while True:
                        rand_follower = users_df.sample()['username'].values[0]
                        # Users can not follow their Reading Lists
                        if rand_follower != row['username']:
                            break

                    query = (
                            "MATCH (a:User), (b:ReadingList) "
                            "WHERE a.username = $username1 AND (b.owner = $username2 AND b.title = $title) "
                            "CREATE (a)-[r:FOLLOWS]->(b)"
                    )

                    session.write_transaction(lambda tx: tx.run(query, username1=rand_follower,
                                                                username2=row['username'], title=reading_list['title']))


            db.Users.update_one({'username': row['username']}, {'$set': {'readingLists': reading_lists}})

        print("Added Reading Lists and Follows")

        ### User Follows and Likes
        for index, row in users_df.iterrows():
            query = (
                    "MATCH (a:User), (b:User) "
                    "WHERE a.username = $username1 AND b.username = $username2 "
                    "CREATE (a)-[r:FOLLOWS]->(b)"
            )

            n_follows = int(random.random() * 11)
            for i in range(0, n_follows):
                while True:
                    rand_user = users_df.sample()['username'].values[0]
                    # Users can not follow themselves
                    if rand_user != row['username']:
                        break
                session.write_transaction(lambda tx: tx.run(query, username1=row['username'], username2=rand_user))

            query = (
                    "MATCH (a:User), (b:Paper) "
                    "WHERE a.username = $username AND (b.arxiv_id = $arxiv_id OR b.vixra_id = $vixra_id) "
                    "CREATE (a)-[r:LIKES]->(b)"
            )

            n_follows = int(random.random() * 11)
            for i in range(0, n_follows):
                rand_paper = papers_df.sample()
                session.write_transaction(lambda tx: tx.run(query, username=row['username'],
                                                            arxiv_id=rand_paper['arxiv_id'].values[0],
                                                            vixra_id=rand_paper['vixra_id'].values[0]))

        print("Added User Follows and Likes")

        ### Special Users

        admin = {
            "username": "admin",
            "email": "admin@gmail.com",
            "password": "admin",
            "firstName": "",
            "lastName": "",
            "picture": "",
            "age": -1,
            "readingLists": [],
            "type": 2
        }
        users_col.insert_one(admin)

        query = ("CREATE (u:User { username: $username, email: $email }) ")
        session.write_transaction(lambda tx: tx.run(query, username='admin', email='admin'))
        print("Added Administrator")

        for i in range(0,5):
            username = "moderator" + str(i)
            moderator = {
                "username": username,
                "email": username + "@gmail.com",
                "password": username,
                "password": "admin",
                "firstName": "",
                "lastName": "",
                "picture": "",
                "age": -1,
                "readingLists": [],
                "type": 1
            }
            users_col.insert_one(moderator)
            query = ("CREATE (u:User { username: $username, email: $email}) ")
            session.write_transaction(lambda tx: tx.run(query, username=moderator['username'], email=moderator['email']))

        print("Added Moderators")

        session.close()


    def do_updateDB(self, arg):
        'Download latest papers'

        # Get Database
        db = self.mongo_client["PaperRater"]
        papers_col = db["Papers"]
        doc = papers_col.find().sort('published', -1).limit(1)
        for x in doc:
            last_date_uploaded = x['published']

        t = time.strptime(last_date_uploaded, '%Y-%m-%d')
        newdate = date(t.tm_year, t.tm_mon, t.tm_mday) + timedelta(1)

        last_date_uploaded = newdate.strftime('%Y-%m-%d')

        # Import latest papers
        print("Last update: ", last_date_uploaded)
        papers_path = getPapers.import_data(last_date_uploaded)


        papers_df = pd.read_json(papers_path, lines=True)

        insert_papers(papers_df)


    def do_exit(self, arg):
        'Exit PaperRater DB_Updater'
        self.mongo_client.close()
        self.neo4j_driver.close()
        sys.exit()

    def insert_papers(self, papers_df):
        db = self.mongo_client["PaperRater"]
        papers_col = db["Papers"]

        # abstract is a Java 8 keyword
        papers_df = papers_df.rename(columns={"abstract": "_abstract"})

        # Split the dataframe in order not to insert "nan" values in mongodb
        arxiv_df = papers_df
        arxiv_df = arxiv_df.drop(columns={"vixra_id"})
        arxiv_df.dropna(subset=["arxiv_id"], inplace=True)
        arxiv_df['arxiv_id'] = arxiv_df['arxiv_id'].map(str)
        arxiv_dict = arxiv_df.to_dict("records")

        vixra_df = papers_df
        vixra_df = vixra_df.drop(columns={"arxiv_id"})
        vixra_df.dropna(subset=["vixra_id"], inplace=True)
        vixra_df['vixra_id'] = vixra_df['vixra_id'].map(str)
        vixra_dict = vixra_df.to_dict("records")

        # Converts IDs to str
        papers_df['arxiv_id'] = papers_df['arxiv_id'].map(str)
        papers_df['vixra_id'] = papers_df['vixra_id'].map(str)

        papers_col.insert_many(arxiv_dict)
        papers_col.insert_many(vixra_dict)

        session = self.neo4j_driver.session()
        for index, row in papers_df.iterrows():
            # Split the dataframe in order not to insert "nan" values in neo4j
            if row['arxiv_id'] != "nan":
                query = ("CREATE (p:Paper { arxiv_id: $arxiv_id, title: $title, category: $category,"
                         " authors: $authors, published: $published}) ")

                session.write_transaction(lambda tx: tx.run(query, arxiv_id=row['arxiv_id'],
                                                            title=row['title'], category=row['category'],
                                                            authors=row['authors'],
                                                            published=row['published']))
            else:
                query = (
                    "CREATE (p:Paper { vixra_id: $vixra_id, title: $title, category: $category,"
                    " authors: $authors, published: $published}) ")

                session.write_transaction(lambda tx: tx.run(query, vixra_id=row['vixra_id'],
                                                            title=row['title'], category=row['category'],
                                                            authors=row['authors'],
                                                            published=row['published']))

        print("Added papers to databases")


if __name__ == '__main__':
    App().cmdloop()
