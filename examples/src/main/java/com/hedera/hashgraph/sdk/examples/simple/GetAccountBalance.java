package com.hedera.hashgraph.sdk.examples.simple;

import com.hedera.hashgraph.sdk.HederaException;
import com.hedera.hashgraph.sdk.examples.ExampleHelper;

public final class GetAccountBalance {
  /*Constructor*/
  private GetAccountBalance() { }

  public static void main(String[] args) throws HederaException {
    AccountId operatorId1;
    Client client1;
    long balance1;
    
    operatorId1 = ExampleHelper.getOperatorId();
    client1 = ExampleHelper.createHederaClient();
    balance1 = client.getAccountBalance(operatorId);

    System.out.println("Account balance = " + balance);
  }/*main*/
}/*GetAccountBalance*/
