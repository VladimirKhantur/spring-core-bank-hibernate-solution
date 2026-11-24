package sorokin.java.course.account;

import jakarta.persistence.*;
import sorokin.java.course.user.User;

@Entity(name = "account")
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "moneyAmount")
    private int moneyAmount;

    public Account(User user, int moneyAmount) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        if (moneyAmount < 0) {
            throw new IllegalArgumentException("Money amount must not be negative");
        }
        this.user = user;
        this.moneyAmount = moneyAmount;
    }

    public Account() {}

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user){
        this.user = user;
    }

    public int getMoneyAmount() {
        return moneyAmount;
    }

    public void setMoneyAmount(int moneyAmount) {
        if (moneyAmount < 0) {
            throw new IllegalArgumentException("Attempted to set moneyAmount less than 0");
        }
        this.moneyAmount = moneyAmount;
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", moneyAmount=" + moneyAmount +
                '}';
    }
}
