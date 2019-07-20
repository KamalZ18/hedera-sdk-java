package com.hedera.hashgraph.sdk;

import com.hedera.hashgraph.sdk.account.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountInfo;
import com.hedera.hashgraph.sdk.account.AccountInfoQuery;
import com.hedera.hashgraph.sdk.account.CryptoTransferTransaction;
import com.hedera.hashgraph.sdk.crypto.Key;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

public final class Client {
    final Random random = new Random();
    private Map<AccountId, Node> channels;

    static final long DEFAULT_MAX_TXN_FEE = 100_000;

    // todo: transaction fees should be defaulted to whatever the transaction fee schedule is
    private long maxTransactionFee = DEFAULT_MAX_TXN_FEE;

    @Nullable
    private AccountId operatorId;

    @Nullable
    private Ed25519PrivateKey operatorKey;

    /*Client constructor*/
    public Client(Map<AccountId, String> nodes) {

        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("List of nodes must not be empty");
        }

        channels = nodes.entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, tNode -> new Node(tNode.getKey(), tNode.getValue())));
    }/*constructor Client*/

    public Client setMaxTransactionFee(@Nonnegative long maxTransactionFee) {
        if (maxTransactionFee <= 0) {
            throw new IllegalArgumentException("maxTransactionFee must be > 0");
        }

        this.maxTransactionFee = maxTransactionFee;
        return this;
    }

    public Client setOperator(AccountId operatorId, Ed25519PrivateKey operatorKey) {
        this.operatorId = operatorId;
        this.operatorKey = operatorKey;
        return this;
    }

    public long getMaxTransactionFee() {
        return maxTransactionFee;
    }

    @Nullable
    AccountId getOperatorId() {
        return operatorId;
    }

    @Nullable
    Ed25519PrivateKey getOperatorKey() {
        return operatorKey;
    }

    Node pickNode() {
      Iterator<Node> channelIter1;
      int idx, r;

      idx = r = 0;
      if (channels.isEmpty()) {
            throw new IllegalStateException("List of channels has become empty");
      }
        
      /*select a Node at random*/
      r = random.nextInt(channels.size());
        
      /*Set up a Node Iterator*/
      channelIter1 = channels.values().iterator();

      /*Traverse through the channel elements (nodes) using the iterator,
	    until we get to the randomly selected node.*/

      for (idx = 1; idx < r; idx++) {
            channelIter1.next();
      }/*for loop*/

        return (channelIter1.next());
    }/*pickNode*/

    Node getNodeForId(AccountId node) {
      Node selectedChannel1;

	  selectedChannel1 = null;
      selectedChannel1 = channels.get(node);

      if (selectedChannel1 == null) {
         throw new IllegalArgumentException("Node Id does not exist");
      }

      return (selectedChannel1);
    }/*getNodeForId*/

    //
    // Simplified interface intended for high-level, opinionated operation
    //

    public AccountId createAccount(Key publicKey, long initialBalance) throws HederaException, HederaNetworkException {
      TransactionReceipt receipt1;
        
      receipt1 = null;
      receipt1 = new AccountCreateTransaction(this).setKey(publicKey)
            .setInitialBalance(initialBalance)
            .executeForReceipt();

        return (receipt1.getAccountId());
    }/*createAccount*/

    public void createAccountAsync(Key publicKey, long initialBalance, Consumer<AccountId> onSuccess, Consumer<HederaThrowable> onError) {
        new AccountCreateTransaction(this).setKey(publicKey)
            .setInitialBalance(initialBalance)
            .executeForReceiptAsync(receipt -> onSuccess.accept(receipt.getAccountId()), onError);
    }

    public AccountInfo getAccount(AccountId id) throws HederaException, HederaNetworkException {
        return new AccountInfoQuery(this).setAccountId(id)
            .execute();
    }

    public void getAccountAsync(AccountId id, Consumer<AccountInfo> onSuccess, Consumer<HederaThrowable> onError) {
        new AccountInfoQuery(this).setAccountId(id)
            .executeAsync(onSuccess, onError);
    }

    public long getAccountBalance(AccountId id) throws HederaException, HederaNetworkException {
        return new AccountBalanceQuery(this).setAccountId(id)
            .execute();
    }

    public void getAccountBalanceAsync(AccountId id, Consumer<Long> onSuccess, Consumer<HederaThrowable> onError) {
        new AccountBalanceQuery(this).setAccountId(id)
            .executeAsync(onSuccess, onError);
    }

    public TransactionId transferCryptoTo(AccountId recipient, long amount) throws HederaException, HederaNetworkException {
        return new CryptoTransferTransaction(this).addSender(Objects.requireNonNull(operatorId), amount)
            .addRecipient(recipient, amount)
            .execute();
    }

    public void transferCryptoToAsync(AccountId recipient, long amount, Consumer<TransactionId> onSuccess, Consumer<HederaThrowable> onError) {
        new CryptoTransferTransaction(this).addSender(Objects.requireNonNull(operatorId), amount)
            .addRecipient(recipient, amount)
            .executeAsync(onSuccess, onError);
    }
}/*Client class*/
