package it.unipi.dii.lsmd.paperraterapp.model;

import java.util.List;

public class ReadingList {
    private String title;
    private List<Paper> papers;

    public ReadingList(String title, List<Paper> papers) {
        this.title = title;
        this.papers = papers;
    }

    public ReadingList(String title) {
        this(title, null);
    }

    public String getTitle() {
        return title;
    }

    public List<Paper> getPapers() {
        return papers;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPaper(List<Paper> papers) { this.papers = papers; }

    @Override
    public String toString() {
        return "ReadingList{" + "papers='" + papers + '\'' + '}';
    }
}

