package appointmentManager;

import java.util.Locale;

public class User {
    private int id;
    private String username;
    private Locale language;

    public User() {}

    public User(int id, String username, Locale language) {
        this.id = id;
        this.username = username;
        this.language = language;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Locale getLanguage() {
        return language;
    }

    public void setLanguage(Locale language) {
        this.language = language;
    }
}
