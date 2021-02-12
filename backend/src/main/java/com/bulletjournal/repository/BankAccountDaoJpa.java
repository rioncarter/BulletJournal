package com.bulletjournal.repository;

import com.bulletjournal.authz.AuthorizationService;
import com.bulletjournal.authz.Operation;
import com.bulletjournal.contents.ContentType;
import com.bulletjournal.controller.models.*;
import com.bulletjournal.exceptions.ResourceNotFoundException;
import com.bulletjournal.repository.models.BankAccount;
import com.bulletjournal.repository.utils.DaoHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BankAccountDaoJpa {

    @Autowired
    private BankAccountRepository bankAccountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private AuthorizationService authorizationService;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<com.bulletjournal.controller.models.BankAccount> getBankAccounts(String requester) {
        List<BankAccount> bankAccounts = this.bankAccountRepository.findAllByOwner(requester);
        // TODO: return bankAccounts.stream().map(b -> b.toPresentationModel(this)).collect(Collectors.toList());
        return bankAccounts.stream().map(BankAccount::toPresentationModel).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public BankAccount getBankAccount(String requester, Long bankAccountId) {
        if (bankAccountId == null) {
            return null;
        }
        BankAccount bankAccount = this.bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank Account " + bankAccountId + " not found"));
        // check access
        this.authorizationService.checkAuthorizedToOperateOnContent(
                bankAccount.getOwner(), requester, ContentType.BANK_ACCOUNT,
                Operation.READ, bankAccountId);

        return bankAccount;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public com.bulletjournal.controller.models.BankAccount create(CreateBankAccountParams createBankAccountParams, String owner) {
        BankAccount bankAccount = new BankAccount();
        bankAccount.setOwner(owner);
        bankAccount.setName(createBankAccountParams.getName());
        bankAccount.setAccountType(createBankAccountParams.getAccountType());
        bankAccount.setDescription(createBankAccountParams.getDescription());
        bankAccount.setAccountNumber(createBankAccountParams.getAccountNumber());
        bankAccount = this.bankAccountRepository.save(bankAccount);
        return bankAccount.toPresentationModel();
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public com.bulletjournal.controller.models.BankAccount partialUpdate(String requester, Long bankAccountId,
                                                                         UpdateBankAccountParams updateBankAccountParams) {
        BankAccount bankAccount = getBankAccount(requester, bankAccountId);

        // check access
        this.authorizationService.checkAuthorizedToOperateOnContent(
                bankAccount.getOwner(), requester, ContentType.BANK_ACCOUNT,
                Operation.UPDATE, bankAccountId);

        DaoHelper.updateIfPresent(updateBankAccountParams.hasName(), updateBankAccountParams.getName(),
                bankAccount::setName);

        // description, account number, type
        DaoHelper.updateIfPresent(updateBankAccountParams.hasDescription(), updateBankAccountParams.getDescription(),
                bankAccount::setDescription);
        DaoHelper.updateIfPresent(updateBankAccountParams.hasAccountNumber(), updateBankAccountParams.getAccountNumber(),
                bankAccount::setAccountNumber);
        DaoHelper.updateIfPresent(updateBankAccountParams.hasAccountType(), updateBankAccountParams.getAccountType(),
                bankAccount::setAccountType);

        // TODO: return this.bankAccountRepository.save(bankAccount).toPresentationModel(this);
        return this.bankAccountRepository.save(bankAccount).toPresentationModel();
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void deleteBankAccount(String requester, Long bankAccountId) {
        BankAccount bankAccount = getBankAccount(requester, bankAccountId);
        this.authorizationService.checkAuthorizedToOperateOnContent(bankAccount.getOwner(), requester, ContentType.BANK_ACCOUNT,
                Operation.DELETE, bankAccountId);

        this.bankAccountRepository.delete(bankAccount);
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public List<Transaction> getTransactions(
            Long bankAccountId, ZonedDateTime startTime, ZonedDateTime endTime, String requester) {
        BankAccount bankAccount = getBankAccount(requester, bankAccountId);
        return null;
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public double getBankAccountBalance(Long bankAccountId) {
        BankAccount bankAccount = this.bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Bank Account " + bankAccountId + " not found"));
        // add recurring amount
        return bankAccount.getNetBalance() +
                this.transactionRepository.getTransactionsAmountSumByBankAccount(bankAccountId);
    }
}
