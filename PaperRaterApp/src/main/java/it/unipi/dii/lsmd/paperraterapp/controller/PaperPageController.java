package it.unipi.dii.lsmd.paperraterapp.controller;

import com.mongodb.client.result.UpdateResult;
import it.unipi.dii.lsmd.paperraterapp.model.*;
import it.unipi.dii.lsmd.paperraterapp.persistence.MongoDBManager;
import it.unipi.dii.lsmd.paperraterapp.persistence.MongoDriver;
import it.unipi.dii.lsmd.paperraterapp.persistence.Neo4jDriver;
import it.unipi.dii.lsmd.paperraterapp.persistence.Neo4jManager;
import it.unipi.dii.lsmd.paperraterapp.utils.Utils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.net.URL;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

public class PaperPageController implements Initializable {
    private Paper paper;
    private User user;
    private MongoDBManager mongoMan;
    private Neo4jManager neoMan;

    @FXML private ImageView backIcon;
    @FXML private Text title;
    @FXML private Text id;
    @FXML private Text category;
    @FXML private Text authors;
    @FXML private Text published;
    @FXML private Text likes;
    @FXML private VBox commentsBox;
    @FXML private Button addToReadingList;
    @FXML private Text abstractPaper;
    @FXML private Button comment;
    @FXML private TextField commentText;
    @FXML private Text comNum;
    @FXML private ScrollPane scrollpane;
    @FXML private Button likebtn;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        neoMan = new Neo4jManager(Neo4jDriver.getInstance().openConnection());
        mongoMan = new MongoDBManager(MongoDriver.getInstance().openConnection());
        backIcon.setOnMouseClicked(mouseEvent -> clickOnBackIcon(mouseEvent));
        addToReadingList.setOnMouseClicked(mouseEvent -> clickOnAddToReadingListBtn(mouseEvent));
        likebtn.setOnMouseClicked(mouseEvent -> clickLike(mouseEvent));
        comment.setOnMouseClicked(mouseEvent -> clickOnAddCommentBtn(mouseEvent));
        scrollpane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    public void setPaperPage (Paper p) {
        this.paper = mongoMan.getPaperById(p.getId());
        this.user = Session.getInstance().getLoggedUser();

        // Push
        Session.getInstance().getPreviousPagePaper().add(p);

        title.setText(paper.getTitle());
        id.setText(paper.getId());
        category.setText(paper.getCategory());
        authors.setText(paper.getAuthors().toString());
        Format formatter = new SimpleDateFormat("yyyy-MM-dd");
        published.setText(formatter.format(paper.getPublished()));
        abstractPaper.setText(paper.getAbstract());
        if(neoMan.userLikePaper(user.getUsername(), paper.getId()))
            likebtn.setText("Unlike");
        else
            likebtn.setText("Like");
        likes.setText(Integer.toString(neoMan.getNumLikes(paper.getId())));
        setCommentBox();
    }

    private void setCommentBox() {
        int numComment = 0;
        if (paper.getComments() != null) {
            commentsBox.getChildren().clear();
            Iterator<Comment> it = paper.getComments().iterator();

            while(it.hasNext()) {
                VBox row = new VBox();
                Comment c = it.next();
                Pane p = loadCommentCard(c, paper);
                row.getChildren().addAll(p);
                commentsBox.getChildren().add(row);
                numComment++;
            }
        }
        comNum.setText(String.valueOf(numComment));
    }

    private Pane loadCommentCard (Comment c, Paper p) {
        Pane pane = null;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/it/unipi/dii/lsmd/paperraterapp/layout/comment_card.fxml"));
            pane = loader.load();
            CommentCtrl ctrl = loader.getController();
            ctrl.textProperty().bindBidirectional(comNum.textProperty());
            ctrl.setCommentCard(c, user.getUsername(), p);

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return pane;
    }

    private void clickOnBackIcon (MouseEvent mouseEvent) {
        // Pop
        Session.getInstance().getPreviousPagePaper().remove(
                Session.getInstance().getPreviousPagePaper().size() - 1);

        if (Session.getInstance().getPreviousPageReadingList().isEmpty())
            Utils.changeScene("/it/unipi/dii/lsmd/paperraterapp/layout/browser.fxml", mouseEvent);
        else {
            ReadingListPageController ctrl = (ReadingListPageController) Utils.changeScene(
                    "/it/unipi/dii/lsmd/paperraterapp/layout/readinglistpage.fxml", mouseEvent);
            String previousUser = Session.getInstance().getPreviousPageUser().get(
                    Session.getInstance().getPreviousPageUser().size()-1).getUsername();

            ctrl.setReadingList(Session.getInstance().getPreviousPageReadingList().remove(
                    Session.getInstance().getPreviousPageReadingList().size() - 1),
                    Session.getInstance().getPreviousPageUser().remove(
                            Session.getInstance().getPreviousPageUser().size() - 1).getUsername());
        }
    }

    private void clickOnAddToReadingListBtn (MouseEvent mouseEvent) {
        if (!Session.getInstance().getLoggedUser().getReadingLists().isEmpty()) {
            Iterator<ReadingList> it = Session.getInstance().getLoggedUser().getReadingLists().iterator();
            List<String> choices = new ArrayList<>();
            while(it.hasNext()) {
                choices.add(it.next().getTitle());
            }
            ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
            dialog.setTitle("Choose reading list");
            dialog.setHeaderText(null);
            dialog.setContentText("Reading list:");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()){
                UpdateResult res = mongoMan.addPaperToReadingList(Session.getInstance().getLoggedUser().getUsername(), result.get(), paper);
                if(res.getModifiedCount() == 0){
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Information Dialog");
                    alert.setHeaderText(null);
                    alert.setContentText("This paper is already present in this reading list!");
                    alert.showAndWait();
                }
                else {
                    // Update Session User Object
                    for (ReadingList r : Session.getInstance().getLoggedUser().getReadingLists()) {
                        if (r.getTitle().equals(result.get())) {
                            r.getPapers().add(paper);
                            break;
                        }
                    }
                }
            }
        }
        else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information Dialog");
            alert.setHeaderText(null);
            alert.setContentText("You haven't created a reading list yet!");
            alert.showAndWait();
        }
    }

    private void clickOnAddCommentBtn (MouseEvent mouseEvent){
        if(!commentText.getText().isEmpty()){
            mongoMan.addComment(paper.getId(), commentText.getText(), user.getUsername());
            paper = mongoMan.getPaperById(paper.getId());
            neoMan.hasCommented(user.getUsername(), paper.getId());
            setCommentBox();
            commentText.setText("");

        }else{
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information Dialog");
            alert.setHeaderText(null);
            alert.setContentText("Inser a commnet!");
            alert.showAndWait();
        }
    }

    private void clickLike (MouseEvent mouseEvent){
        if(Objects.equals(likebtn.getText(), "Like")){
            neoMan.like(user, paper);
            likes.setText(Integer.toString(neoMan.getNumLikes(paper.getId())));
            likebtn.setText("UnLike");
        }else{
            neoMan.unlike(user, paper);
            likes.setText(Integer.toString(neoMan.getNumLikes(paper.getId())));
            likebtn.setText("Like");
        }
    }
}
