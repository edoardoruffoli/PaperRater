package it.unipi.dii.lsmd.paperraterapp.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import it.unipi.dii.lsmd.paperraterapp.model.*;
import javafx.util.Pair;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.inc;

/**
 * MongoDB Queries Managers
 */
public class MongoDBManager {
    public MongoDatabase db;
    private MongoCollection usersCollection;
    private MongoCollection papersCollection;

    public MongoDBManager(MongoClient client) {
        this.db = client.getDatabase("PaperRater");
        usersCollection = db.getCollection("Users");
        papersCollection = db.getCollection("Papers");
    }

    /**
     * Method used to perform the login
     * @param username User that is logging in
     * @param password
     * @return User informations related to the username
     */
    public User login (String username, String password) {
        Document result = (Document) usersCollection.find(Filters.and(eq("username", username),
                                                                eq("password", password))).
                                                                first();

        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result), User.class);
    }

    /**
     * Add a new User to MongoDB
     * @param u The object User which contains all the necessary information
     * @return true if operation is successfully executed, false otherwise
     */
    public boolean addUser (User u) {
        try {
            Document doc = new Document("username", u.getUsername())
                                .append("email", u.getEmail())
                                .append("password", u.getPassword());

            if (u.getFirstName() != null)
                doc.append("firstName", u.getFirstName());
            if (u.getLastName() != null)
                doc.append("lastName", u.getLastName());
            if (u.getAge() != -1)
                doc.append("age", u.getAge());

            doc.append("readingLists", u.getReadingLists());

            usersCollection.insertOne(doc);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Function that deletes the user from the database
     * @param u user to delete
     * @return true if operation is successfully executed, false otherwise
     */
    public boolean deleteUser(User u) {
        try {
            Bson find = eq("comments.username", u.getUsername());
            Bson update = Updates.set("comments.$.username", "Deleted user");
            papersCollection.updateMany(find, update);
            usersCollection.deleteOne(eq("username", u.getUsername()));
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Edit an already present user
     * @param u the new user information to replace the old one
     * @return true if operation is successfully executed, false otherwise
     */
    public boolean updateUser (User u){
        try {
            Document doc = new Document().append("username", u.getUsername());
            if (!u.getEmail().isEmpty())
                doc.append("email", u.getEmail());
            if (!u.getPassword().isEmpty())
                doc.append("password", u.getPassword());
            if (!u.getFirstName().isEmpty())
                doc.append("firstName", u.getFirstName());
            if (!u.getLastName().isEmpty())
                doc.append("lastName", u.getLastName());
            if (u.getAge() != -1)
                doc.append("age", u.getAge());
            doc.append("type", u.getType());

            Bson updateOperation = new Document("$set", doc);
            usersCollection.updateOne(new Document("username", u.getUsername()), updateOperation);
            return true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Method that searches a user by his username
     * @param username username of the user
     * @return User
     */
    public User getUserByUsername (String username) {
        Document result = (Document) usersCollection.find((eq("username", username))).first();
        if (result == null) {
            return null;
        }

        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(result), User.class);
    }

    /**
     * Method that adds a comment to a paper
     * @param paper Paper Object
     * @param comment text of the comment
     * @return true if operation is successfully executed, false otherwise
     */
    public boolean addComment (Paper paper, Comment comment) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Document doc = new Document("username", comment.getUsername())
                    .append("text", comment.getText())
                    .append("timestamp", dateFormat.format(comment.getTimestamp()));

            Bson find = and(eq("arxiv_id", paper.getArxivId()), eq("vixra_id", paper.getVixraId()));
            Bson update = Updates.addToSet("comments", doc);
            papersCollection.updateOne(find, update);
            return true;
        }
        catch (Exception e)
        {
            System.out.println("Error in adding a comment to a Paper");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Method that updates the list of comments of a paper
     * @param p Paper Object
     * @param comments List of the comments
     * @return true if operation is successfully executed, false otherwise
     */
    public boolean updateComments(Paper p, List<Comment> comments){
        try{
            Bson update = new Document("comments", comments);
            Bson updateOperation = new Document("$set", update);
            if(p.getArxivId() != null)
                papersCollection.updateOne(new Document("arxiv_id", p.getArxivId()), updateOperation);
            else
                papersCollection.updateOne(new Document("vixra_id", p.getVixraId()), updateOperation);
            return true;
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            System.err.println("Error in updating user on MongoDB");
            return false;
        }
    }

    /**
     * Method that updates an existing comment
     * @param paper Paper Object
     * @param comment comment
     */
    public void updateComment(Paper paper, Comment comment){
        List<Comment> comments = paper.getComments();
        int i=0;
        for (Comment c: comments
        ) {
            if(c.getUsername().equals(comment.getUsername()) && c.getTimestamp().equals(
                    comment.getTimestamp())){
                comments.set(i, comment);
                break;
            }
            i++;
        }
        updateComments(paper, comments);
    }

    /**
     * Method that deletes a comment
     * @param paper Paper Object
     * @param comment Comment Object
     */
    public void deleteComment (Paper paper, Comment comment) {
        List<Comment> comments = paper.getComments();
        int n = 0;
        int d = 0;
        for (Comment c : comments){
            if (c.getTimestamp().equals(comment.getTimestamp()) && c.getUsername().equals(comment.getUsername())) {
                d = n;
                break;
            }
            n++;
        }
        comments.remove(d);
        if (Session.getInstance().getLoggedUser().getType() > 0)
            incrementDeletedCommentsCounter(comment.getUsername());
        updateComments(paper, comments);
    }

    public void incrementDeletedCommentsCounter(String username) {
        usersCollection.updateOne(eq("username", username), inc("deletedComments", 1));
    }

    /**
     * Function that return the paper that matches the id
     * @param paper Paper object
     * @return Paper object
     */
    public Paper getPaperById (Paper paper) {
        try {
            Paper p = null;
            Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd hh:mm:ss").create();
            Document myDoc = (Document) papersCollection.find(
                    and(eq("arxiv_id", paper.getArxivId()), eq("vixra_id", paper.getVixraId()))).first();
            p = gson.fromJson(gson.toJson(myDoc), Paper.class);
            return p;
        }
        catch (JsonSyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Method that searches papers given parameters.
     * @param title partial title of the papers to match
     * @param author
     * @param start_date
     * @param end_date
     * @param category
     * @return a list of papers that match the parameters
     */
    public List<Paper> searchPapersByParameters (String title, String author, String start_date,
                                                 String end_date, String category, int skip, int limit) {
        List<Paper> papers = new ArrayList<>();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd hh:mm:ss").create();

        List<Bson> pipeline = new ArrayList<>();

        if (!title.isEmpty()) {
            Pattern pattern1 = Pattern.compile("^.*" + title + ".*$", Pattern.CASE_INSENSITIVE);
            pipeline.add(Aggregates.match(Filters.regex("title", pattern1)));
        }

        if (!author.isEmpty()) {
            Pattern pattern2 = Pattern.compile("^.*" + author + ".*$", Pattern.CASE_INSENSITIVE);
            pipeline.add(Aggregates.match(Filters.regex("authors", pattern2)));
        }

        if (!start_date.isEmpty()) {
            pipeline.add(Aggregates.match(and(Filters.gte("published", start_date))));
        }

        if(!end_date.isEmpty()) {
            pipeline.add(Aggregates.match(and(Filters.lte("published", end_date))));
        }

        if (!category.isEmpty()) {
            pipeline.add(Aggregates.match(Filters.eq("category", category)));
        }

        pipeline.add(sort(descending("published")));
        pipeline.add(skip(skip * limit));
        pipeline.add(limit(limit));

        List<Document> results = (List<Document>) papersCollection.aggregate(pipeline)
                .into(new ArrayList<>());
        Type papersListType = new TypeToken<ArrayList<Paper>>(){}.getType();
        papers = gson.fromJson(gson.toJson(results), papersListType);
        return papers;
    }

    /**
     * Add a new empty reading list at the user identified by username
     * @param user User Object
     * @param title title of the new reading list
     * @return true if it adds the reading list, otherwise it returns false
     */
    public boolean createReadingList(User user, String title) {
        // check if there are other list with the same name
        Document document = (Document) usersCollection.find(and(eq("username", user.getUsername()),
                eq("readingLists.title", title))).first();
        if (document != null) {
            System.err.println("ERROR: name already in use.");
            return false;
        }
        // create the new reading_list
        Document readingList = new Document("title", title)
                .append("papers", Arrays.asList());
        // insert the new reading_list
        usersCollection.updateOne(
                eq("username", user.getUsername()),
                new Document().append(
                        "$push",
                        new Document("readingLists", readingList)
                )
        );
        return true;
    }

    /**
     * Delete the reading list of user by specifying the title
     * @param username username of the user
     * @param title title of the reading list which i want remove
     * @return true if it removes the reading list, otherwise it returns false
     */
    public boolean deleteReadingList(String username, String title){
        try {
            Bson filter = new Document().append("username", username);
            Bson fields = new Document().append("readingLists", new Document("title", title));
            Bson update = new Document("$pull", fields);
            UpdateResult updateResult = usersCollection.updateOne(filter, update);
            if (updateResult.getModifiedCount() == 0) {
                System.err.println("ERROR: can not delete the reading list " + title);
                return false;
            } else {
                return true;
            }
        }
        catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Method that adds a Paper to a ReadingList
     * @param user owner of the ReadingList
     * @param title title of the Reading List
     * @param p Paper
     * @return true if the operation is successfully executed, false otherwise
     */
    public UpdateResult addPaperToReadingList(String user, String title, Paper p) {
        Document paperReduced;
        if (p.getArxivId() != null)
            paperReduced = new Document("arxiv_id", p.getArxivId());
        else
            paperReduced = new Document("vixra_id", p.getVixraId());

        paperReduced.append("title", p.getTitle())
                .append("authors", p.getAuthors())
                .append("category", p.getCategory());

        Bson find = and(eq("username", user),
                eq("readingLists.title", title));
        Bson update = Updates.addToSet("readingLists.$.papers", paperReduced);
        UpdateResult result = usersCollection.updateOne(find, update);
        return result;
    }

    /**
     * Method that remove a Paper from a ReadingList
     * @param user ReadingList
     * @param p Paper
     * @return true if the operation is successfully executed, false otherwise
     */
    public UpdateResult removePaperFromReadingList(String user, String title, Paper p) {
        try {
            Document paperReduced = new Document("arxiv_id", p.getArxivId())
                    .append("vixra_id", p.getVixraId())
                    .append("title", p.getTitle())
                    .append("authors", p.getAuthors())
                    .append("category", p.getCategory());

            Bson find = and(eq("username", user),
                    eq("readingLists.title", title));
            Bson delete = Updates.pull("readingLists.$.papers", paperReduced);
            UpdateResult result = usersCollection.updateOne(find, delete);
            return result;
        }
        catch (Exception e)
        {
            System.out.println("Error in removing a paper from a Reading List");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Method that returns the Reading List given the user and the title
     * @param owner Username of the user
     * @param title Name of the reading list
     * @return  The list of reading lists
     */
    public ReadingList getReadingList(String owner, String title) {
        ReadingList readingList = null;
        Gson gson = new GsonBuilder().serializeNulls().create();

        Bson match = match(eq("username", owner));
        Bson unwind = unwind("$readingLists");
        Bson match2 = match(eq("readingLists.title", title));
        Bson project = project(fields(excludeId(), computed("ReadingList", "$readingLists")));
        MongoCursor<Document> iterator = (MongoCursor<Document>) usersCollection.aggregate(Arrays.asList(match, unwind, match2, project)).iterator();
        if(iterator.hasNext()){
            Document document = iterator.next();
            Document ReadingListDocument = (Document) document.get("ReadingList");
            readingList = gson.fromJson(gson.toJson(ReadingListDocument), ReadingList.class);
        }
        return readingList;
    }

    /**
     * Function that return the ReadingLists given the name
     * @param keyword part of the title
     * @return  The list of reading lists and its owner
     */
    public List<Pair<String, ReadingList>> getReadingListByKeyword (String keyword, int skipDoc, int limitDoc) {
        List<Pair<String, ReadingList>> readingLists = new ArrayList<>();
        Gson gson = new GsonBuilder().serializeNulls().create();
        Bson unwind = unwind("$readingLists");
        Pattern pattern= Pattern.compile("^.*" + keyword + ".*$", Pattern.CASE_INSENSITIVE);
        Bson filter = Aggregates.match(Filters.regex("readingLists.title", pattern));
        Bson skip = skip(skipDoc);
        Bson limit = limit(limitDoc);
        MongoCursor<Document> iterator = (MongoCursor<Document>) usersCollection.aggregate(Arrays.asList(unwind,
                filter, skip, limit)).iterator();
        while(iterator.hasNext()){
            Document document = iterator.next();
            String username = document.getString("username");
            Document ReadingListDocument = (Document) document.get("readingLists");
            ReadingList readingList = gson.fromJson(gson.toJson(ReadingListDocument), ReadingList.class);
            readingLists.add(new Pair<>(username, readingList));
        }
        return readingLists;
    }

    /**
     * Return users the contains the keyword, if we give a list of user
     * the research is added only inside this sublist
     * @param next select the portion of result
     * @param keyword keyword to search users
     * @return list of users
     */
    public List<User> getUsersByKeyword (String keyword, boolean moderator, int next) {
        List<User> results = new ArrayList<>();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        Consumer<Document> convertInUser = doc -> {
            User user = gson.fromJson(gson.toJson(doc), User.class);
            results.add(user);
        };
        Pattern pattern= Pattern.compile("^.*" + keyword + ".*$", Pattern.CASE_INSENSITIVE);
        Bson filter = Aggregates.match(Filters.regex("username", pattern));
        Bson limit = limit(8);
        Bson skip = skip(next*8);
        if (moderator) {
            Bson moderatorFilter = match(eq("type", 1));
            usersCollection.aggregate(Arrays.asList(filter, moderatorFilter, skip, limit)).forEach(convertInUser);
        } else
            usersCollection.aggregate(Arrays.asList(filter, skip, limit)).forEach(convertInUser);
        return results;
    }

    /**
     * Method that returns a list of users whose comments have been deleted at least one time.
     * It is used by admins to ban users who have not respected application's policy.
     *
     * @return List of Users
     */
    public List<User> getBadUsers(int skipDoc, int limitDoc) {
        List<User> results = new ArrayList<>();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        Consumer<Document> convertInUser = doc -> {
            User user = gson.fromJson(gson.toJson(doc), User.class);
            results.add(user);
        };
        Bson filter = match(gte("deletedComments", 1));
        Bson skip = skip(skipDoc);
        Bson limit = limit(limitDoc);
        usersCollection.aggregate(Arrays.asList(filter, skip, limit)).forEach(convertInUser);
        return results;
    }

    /*Users with the highest number of categories in their reading lists  */
    /**
     * Function that return a list of User with the highest number of categories reading lists
     * @param limitDoc First "number" users
     * @param skipDoc Skip users
     * @return  The list of users
     */
    public List<Pair<User, Integer>> getTopVersatileUsers (int skipDoc, int limitDoc) {
        List<Pair<User, Integer>> results = new ArrayList<>();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        Consumer<Document> convertInUser = doc -> {
            User user = gson.fromJson(gson.toJson(doc), User.class);
            results.add(new Pair(user, doc.getInteger("totalCategory")));
        };

        Bson unwind1 = unwind("$readingLists");
        Bson unwind2 = unwind("$readingLists.papers");
        // Distinct occurrences
        Bson groupMultiple = new Document("$group",
                new Document("_id", new Document("username", "$username")
                        .append("email", "$email")
                        .append("password", "$password")
                        .append("firstName", "$firstName")
                        .append("lastName", "$lastName")
                        .append("age", "$age")
                        .append("category", "$readingLists.papers.category")
                ));
        // Sum all occurrences
        Bson group = new Document("$group",
                new Document("_id",
                        new Document("username", "$_id.username")
                                .append("email", "$_id.email")
                                .append("password", "$_id.password")
                                .append("firstName", "$_id.firstName")
                                .append("lastName", "$_id.lastName")
                                .append("age", "$_id.age"))
                        .append("totalCategory",
                                new Document("$sum", 1)));

        Bson project = project(fields(excludeId(),
                computed("username", "$_id.username"),
                computed("email", "$_id.email"),
                computed("password", "$_id.password"),
                computed("firstName", "$_id.firstName"),
                computed("lastName", "$_id.lastName"),
                computed("age", "$_id.age"),
                include("totalCategory")));
        Bson sort = sort(descending("totalCategory"));
        Bson skip = skip(skipDoc);
        Bson limit = limit(limitDoc);

        usersCollection.aggregate(Arrays.asList(unwind1, unwind2, groupMultiple, group, project, sort, skip, limit)).forEach(convertInUser);

        return results;
    }

    /**
     * Browse all comments that has been written "numDays" ago
     * @param start_date start date
     * @param start_date finish date
     * @param skipDoc how many comments skip
     * @param limitDoc limit number of comments
     * @return list of comments
     */
    public List<Pair<Paper, Comment>> searchLastComments(String start_date, String end_date, int skipDoc, int limitDoc) {

        List<Pair<Paper, Comment>> results = new ArrayList<>();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        List<Bson> pipeline = new ArrayList<>();

        Consumer<Document> takeComments = doc -> {

            String arxiv_id = (String) doc.get("arxiv_id");
            String vixra_id = (String) doc.get("vixra_id");
            String title = (String) doc.get("title");
            String _abstract = (String) doc.get("_abstract");
            String category = (String) doc.get("category");
            List<String> authors = (List<String>) doc.get("authors");
            Document docComments = (Document) doc.get("comments");
            Comment comment = gson.fromJson(gson.toJson(docComments), Comment.class);
            Paper paper = new Paper(arxiv_id, vixra_id, title, _abstract, category, authors, null, null );

            results.add(new Pair<>(paper, comment));
        };

        pipeline.add(Aggregates.unwind("$comments"));
        if(!start_date.isEmpty())
            pipeline.add(Aggregates.match(gte("comments.timestamp", start_date)));
        if(!end_date.isEmpty())
            pipeline.add(Aggregates.match(lte("comments.timestamp", end_date)));
        pipeline.add(sort(ascending("comments.timestamp", "comments.username")));
        pipeline.add(skip(skipDoc));
        pipeline.add(limit(limitDoc));

        papersCollection.aggregate(pipeline).forEach(takeComments);
        return results;
    }

    /**
     * Method that returns papers with the highest number of comments in the specified period of time.
     * @param period (all, month, week)
     * @param skipDoc (positive integer)
     * @param limitDoc (positive integer)
     * @return HashMap with the title and the number of comments
     */
    public List<Pair<Paper, Integer>> getMostCommentedPapers(String period, int skipDoc, int limitDoc) {
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalDateTime startOfDay;
        switch (period) {
            case "all" -> startOfDay = LocalDateTime.MIN;
            case "month" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusMonths(1);
            case "week" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusWeeks(1);
            default -> {
                System.err.println("ERROR: Wrong period.");
                return null;
            }
        }
        String filterDate = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Pair<Paper, Integer>> results = new ArrayList<>();
        Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();
        Consumer<Document> convertInPaper = doc -> {
            Paper paper = gson.fromJson(gson.toJson(doc), Paper.class);
            results.add(new Pair(paper, doc.getInteger("totalComments")));
        };

        Bson unwind = unwind("$comments");
        Bson filter = match(gte("comments.timestamp", filterDate));
        Bson group = new Document("$group",
                new Document("_id",
                        new Document("arxiv_id", "$arxiv_id")
                                .append("vixra_id", "$vixra_id")
                                .append("title", "$title")
                                .append("_abstract", "$_abstract")
                                .append("category", "$category")
                                .append("authors", "$authors")
                                .append("published", "$published"))
                        .append("totalComments",
                                new Document("$sum", 1)));
        Bson project = project(fields(excludeId(),
                computed("arxiv_id", "$_id.arxiv_id"),
                computed("vixra_id", "$_id.vixra_id"),
                computed("title", "$_id.title"),
                computed("_abstract", "$_id._abstract"),
                computed("category", "$_id.category"),
                computed("authors", "$_id.authors"),
                computed("published", "$_id.published"),
                include("totalComments")));
        Bson sort = sort(Indexes.descending("totalComments", "published"));
        Bson skip = skip(skipDoc);
        Bson limit = limit(limitDoc);
        papersCollection.aggregate(Arrays.asList(unwind, filter, group, project, sort, skip, limit)).forEach(convertInPaper);

        return results;
    }

    /*Weekly/Monthly/All time summary of the categories by number of papers Published */
    /**
     * Function that return the top categories by the number of papers published
     * @param period initial date
     * @return The list of the most common categories
     */
    public List<Pair<String, Integer>> getCategoriesSummaryByNumberOfPaperPublished (String period){
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalDateTime startOfDay;
        switch (period) {
            case "all" -> startOfDay = LocalDateTime.MIN;
            case "month" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusMonths(1);
            case "week" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusWeeks(1);
            default -> {
                System.err.println("ERROR: Wrong period.");
                return null;
            }
        }
        String filterDate = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Pair<String,Integer>> categories = new ArrayList<>();

        Bson match = match(gte("published", filterDate));
        Bson group = group("$category", sum("totalPaper", 1));
        Bson project = project(fields(excludeId(), computed("category", "$_id"), include("totalPaper")));
        Bson sort = sort(descending("totalPaper"));

        List<Document> results = (List<Document>) papersCollection.aggregate(Arrays.asList(match, group, project, sort)).into(new ArrayList<>());

        for (Document document: results)
        {
            categories.add(new Pair(document.getString("category"), document.getInteger("totalPaper")));
        }
        return categories;
    }

    /**
     * Browse the top categories with more comments
     * @param period (all, month, week)
     * @return HashMap with the category and the number of comments
     */
    public List<Pair<String, Integer>> getCategoriesSummaryByComments(String period) {
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalDateTime startOfDay;
        switch (period) {
            case "all" -> startOfDay = LocalDateTime.MIN;
            case "month" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusMonths(1);
            case "week" -> startOfDay = localDateTime.toLocalDate().atStartOfDay().minusWeeks(1);
            default -> {
                System.err.println("ERROR: Wrong period.");
                return null;
            }
        }
        String filterDate = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<Pair<String, Integer>> results = new ArrayList<>();
        Consumer<Document> rankCategories = doc ->
                results.add(new Pair<>((String) doc.get("_id"), (Integer) doc.get("tots")));

        Bson unwind = unwind("$comments");
        Bson filter = match(gte("comments.timestamp", filterDate));
        Bson group = group("$category", sum("tots", 1));
        Bson sort = sort(Indexes.descending("tots"));
        papersCollection.aggregate(Arrays.asList(unwind, filter, group, sort)).forEach(rankCategories);

        return results;
    }

    public List<String> getCategories() {
        List<String> categoriesList = new ArrayList<>();
        papersCollection.distinct("category", String.class).into(categoriesList);
        return categoriesList;
    }
}
