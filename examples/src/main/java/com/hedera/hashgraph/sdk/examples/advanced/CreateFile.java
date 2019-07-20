package com.hedera.hashgraph.sdk.examples.advanced;

import com.hedera.hashgraph.sdk.HederaException;
import com.hedera.hashgraph.sdk.examples.ExampleHelper;
import com.hedera.hashgraph.sdk.file.FileCreateTransaction;

import java.time.Duration;
import java.time.Instant;

public final class CreateFile {
    /*Constructor*/
    private CreateFile() { }

    public static void main(String[] args) throws HederaException {
      FileId newFileId1;
      FileCreateTransaction filTx1;
      TransactionReceipt txReceipt1;
      Client hederaClient1;
      Ed25519PrivateKey operatorKey1;
      byte[] fileContents1;

      operatorKey1 = ExampleHelper.getOperatorKey();
      hederaClient = ExampleHelper.createHederaClient();

      /* The file is required to be a byte array,
         you can easily use the bytes of a file instead.*/
      fileContents1 = "Hedera hashgraph is great!".getBytes();

      filTx1 = new FileCreateTransaction(client).setExpirationTime(
               Instant.now()
               .plus(Duration.ofSeconds(2592000)))
               // Use the same key as the operator to "own" this file
               .addKey(operatorKey1.getPublicKey())
               .setContents(fileContents1);

        txReceipt1 = filTx1.executeForReceipt();
        newFileId1 = txReceipt1.getFileId();

        System.out.println("New file created OK; New File ID: " + newFileId);
    }/*main*/
}/*CreateFile*/
