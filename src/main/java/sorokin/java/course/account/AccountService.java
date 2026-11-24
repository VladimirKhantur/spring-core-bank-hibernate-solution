package sorokin.java.course.account;

import jakarta.persistence.LockModeType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.springframework.stereotype.Component;
import sorokin.java.course.user.User;
import java.util.*;

@Component
public class AccountService {
    private final AccountProperties accountProperties;
    private final SessionFactory sessionFactory;

    public AccountService(AccountProperties accountProperties, SessionFactory sessionFactory) {
        this.accountProperties = accountProperties;
        this.sessionFactory = sessionFactory;
    }

    public Account createAccount(Long userId) {
        Transaction tx = null;
        try(Session session = sessionFactory.openSession()){
            tx = session.beginTransaction();
            User user = session.find(User.class, userId);
            if(user == null){
                throw new IllegalArgumentException("User not found:" + userId);
            }

            Account account = new Account(user, accountProperties.getDefaultAmount());

            session.persist(account);
            tx.commit();
            return account;
        } catch (RuntimeException e){
            rollback(tx);
            throw e;
        }
    }

    public Account findAccountById(Long id) {
        try (Session session = sessionFactory.openSession()) {
            Account account = session.find(Account.class, id);
            if (account == null) {
                throw new IllegalArgumentException("No such account: id=%s".formatted(id));
            }
            return account;
        }
    }


    public List<Account> getUserAccounts(Long userId) {
        try (Session session = sessionFactory.openSession()) {
            User user = session.find(User.class,userId);
            return session.createQuery("SELECT a FROM account a WHERE a.user = :user", Account.class)
                    .setParameter("user", user)
                    .list();
        }
    }

    public void withdraw(Long fromAccountId, Integer amount) {
        Transaction tx = null;
        validatePositiveId(fromAccountId, "account id");
        validatePositiveAmount(amount);
        try(Session session = sessionFactory.openSession()){
            tx = session.beginTransaction();
            Account account = session.find(Account.class,fromAccountId,LockModeType.PESSIMISTIC_WRITE);
            if (account == null) {
                throw new IllegalArgumentException("Account not found: " + fromAccountId);
            }
            if (amount > account.getMoneyAmount()) {
                throw new IllegalArgumentException(
                        "insufficient funds on account id=%s, moneyAmount=%s, attempted withdraw=%s"
                                .formatted(account.getId(), account.getMoneyAmount(), amount)
                );
            }
            account.setMoneyAmount(account.getMoneyAmount() - amount);
            tx.commit();
        }  catch (RuntimeException e){
            rollback(tx);
            throw e;
        }
    }

    public void deposit(Long toAccountId, Integer amount) {
        validatePositiveId(toAccountId, "account id");
        validatePositiveAmount(amount);
        Transaction tx = null;
        try(Session session = sessionFactory.openSession()){
            tx = session.beginTransaction();
            Account account = session.find(Account.class,toAccountId,LockModeType.PESSIMISTIC_WRITE);
            if (account == null) {
                throw new IllegalArgumentException(
                        "Account not found: " + toAccountId
                );
            }
            account.setMoneyAmount(account.getMoneyAmount() + amount);
            tx.commit();
        } catch (RuntimeException e){
            rollback(tx);
            throw e;
        }
    }

    public Account closeAccount(Long accountId) {
        validatePositiveId(accountId, "account id");
        Transaction tx = null;
        try(Session session = sessionFactory.openSession()){
            tx = session.beginTransaction();
            Account accountToClose = session.find(Account.class,accountId,LockModeType.PESSIMISTIC_WRITE);
            if (accountToClose == null) {
                throw new IllegalArgumentException("Account not found: " + accountId
                );
            }

            List<Account> userAccounts = session.createQuery("SELECT a FROM account a WHERE a.user.id = :userId", Account.class)
                    .setParameter("userId",accountToClose.getUser().getId()).stream().toList();
            if (userAccounts.size() == 1) {
                throw new IllegalStateException("Can't close the only one account");
            }
            Account accountToTransferMoney = userAccounts.stream()
                    .filter(a -> !a.getId().equals(accountToClose.getId()))
                    .min(Comparator.comparingLong(Account::getId))
                    .orElseThrow();

            var newAmount = Math.addExact(accountToTransferMoney.getMoneyAmount(), accountToClose.getMoneyAmount());
            accountToTransferMoney.setMoneyAmount(newAmount);
            session.remove(accountToClose);
            tx.commit();
            return accountToClose;
        } catch (RuntimeException e){
            rollback(tx);
            throw e;
        }
    }

    public void transfer(Long fromAccountId, Long toAccountId, int amount) {
        validatePositiveId(fromAccountId, "source account id");
        validatePositiveId(toAccountId, "target account id");
        validatePositiveAmount(amount);
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("source and target account id must be different");
        }

        Transaction tx = null;
        try(Session session = sessionFactory.openSession()){
            tx = session.beginTransaction();
            //Блокируем на время работы с аккаунтом
            Account accountFrom = session.find(Account.class, fromAccountId, LockModeType.PESSIMISTIC_WRITE);
            Account accountTo = session.find(Account.class, toAccountId, LockModeType.PESSIMISTIC_WRITE);

            if (accountFrom == null) {
                throw new IllegalArgumentException(
                        "Source account not found: " + fromAccountId
                );
            }

            if (accountTo == null) {
                throw new IllegalArgumentException(
                        "Target account not found: " + toAccountId
                );
            }

            if (amount > accountFrom.getMoneyAmount()) {
                throw new IllegalArgumentException(
                        "insufficient funds on account id=%s, moneyAmount=%s, attempted transfer=%s"
                                .formatted(accountFrom.getId(), accountFrom.getMoneyAmount(), amount)
                );
            }

            int amountToTransfer = Objects.equals(accountTo.getUser().getId(), accountFrom.getUser().getId())
                    ? amount
                    : (int) Math.round(amount * (1 - accountProperties.getTransferCommission()));

            int sourceAfter = Math.subtractExact(accountFrom.getMoneyAmount(), amount);
            int targetAfter = Math.addExact(accountTo.getMoneyAmount(), amountToTransfer);


            accountFrom.setMoneyAmount(sourceAfter);
            accountTo.setMoneyAmount(targetAfter);

            tx.commit();
        } catch (RuntimeException e){
            rollback(tx);
            throw e;
        }
    }

    private void validatePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    private void validatePositiveAmount(Integer amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
    }

    private void rollback(Transaction tx){
        if(tx != null && tx.isActive()){
            tx.rollback();
        }
    }
}
