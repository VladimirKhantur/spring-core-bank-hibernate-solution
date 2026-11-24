package sorokin.java.course.user;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.stereotype.Component;
import sorokin.java.course.account.Account;
import sorokin.java.course.account.AccountProperties;
import sorokin.java.course.account.AccountService;
import sorokin.java.course.user.User;

import java.util.*;

@Component
public class UserService {
    private final SessionFactory sessionFactory;
    private final AccountProperties accountProperties;

    public UserService(SessionFactory sessionFactory, AccountProperties accountProperties) {
        this.sessionFactory = sessionFactory;
        this.accountProperties = accountProperties;
    }

    public User createUser(String login) {
        String normalizedLogin = validateLogin(login);

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();

            try {
                if (loginExists(session, normalizedLogin)) {
                    throw new IllegalArgumentException("User with login '%s' already exists".formatted(normalizedLogin));
                }
                User user = new User(normalizedLogin, new ArrayList<>());
                session.persist(user);
                Account account = new Account(user, accountProperties.getDefaultAmount());
                user.addAccount(account);
                session.persist(account);
                tx.commit();
                return user;
            } catch (RuntimeException e) {
                rollback(tx, e);
                throw e;
            }
        }
    }

    public User findUserById(Long id) {
        try(Session session = sessionFactory.openSession()){
            return session.find(User.class,id);
        }
    }

    public List<User> findAll() {
        try(Session session = sessionFactory.openSession()){
            return session
                    .createQuery("SELECT DISTINCT u from user u LEFT JOIN FETCH u.accountList", User.class)
                    .list();

        }
    }

    private String validateLogin(String login) {
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("login must not be blank");
        }
        return login.trim();
    }

    private boolean loginExists(Session session, String login) {
        return session.createQuery("SELECT COUNT(u) FROM user u WHERE u.login = :login", Long.class)
                .setParameter("login", login)
                .getSingleResult() > 0;
    }

    private void rollback(Transaction tx, RuntimeException originalException) {
        if (tx != null && tx.isActive()) {
            try {
                tx.rollback();
            } catch (RuntimeException rollbackException) {
                originalException.addSuppressed(rollbackException);
            }
        }
    }
}
