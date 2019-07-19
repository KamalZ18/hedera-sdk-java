package com.hedera.hashgraph.sdk.examples;

/*Android SDK classes*/
import android.support.annotation.NonNull;
import android.util.Log;

/*Hedera Java SDK classes*/
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ExampleHelper {
  private static final String LogTag = "ExampleHelper";

  /*constructor ExampleHelper*/
  public ExampleHelper() { }

  private static Dotenv getEnv() {
  /* The environment file on Android devices is called node.env,
     Load configuration from the environment --> load from the node.env (.env) file,
     See .env.sample for an example of what it is looking for.
     Load configuration from the environment or an node.env file,
     location of node.env file on Android device: /data/local/tmp
     the node.env file should contain addresses of 4 nodes.*/
	  Dotenv dotEnvFile1;
    
    /*the call to the directory method sets the location of where to find the node.env file,
      change this string to where you would like to place your node.env file.*/
    try {
    dotEnvFile1 = Dotenv.configure()
					    .directory("/data/local/tmp")
              .filename("node.env")
              .load();
    } catch (Exception Exp1) {
      Log.i (LogTag,"getEnv --> Dotenv --> Exception: " + Exp1.toString());
      Log.i (LogTag,"getEnv --> Dotenv Error: Cannot find node.env file.");
      Exp1.printStackTrace();
    }/*try ... catch*/
    return (dotEnvFile1);
  }/*getEnv*/

  public static AccountId getNodeId() {
    AccountId acountId1;
    acountId1 = AccountId.fromString(Objects.requireNonNull(getEnv().get("NODE_ID")));
    return AccountId.fromString(Objects.requireNonNull(getEnv().get("NODE_ID")));
  }/*getNodeId*/

  public static AccountId getOperatorId() {
        return AccountId.fromString(Objects.requireNonNull(getEnv().get("OPERATOR_ID")));
  }/*getOperatorId*/

  public static Ed25519PrivateKey getOperatorKey() {
      return Ed25519PrivateKey.fromString(Objects.requireNonNull(getEnv().get("OPERATOR_KEY")));
  }/*getOperatorKey*/

  public static Client createHederaClient() {
  /* To connect to a network with more nodes, add additional entries to the network map
     This method connects to a network with 1 nodes. */
    Client client1;
    String nodeAddres1,
 
    nodeAddres1 = Objects.requireNonNull(getEnv().get("NODE_ADDRESS"));
    
    /*Create a new Client with 1 node.*/
    client1 = new Client(Map.of(getNodeId(), nodeAddres1));

    // Defaults the operator account ID and key such that all generated transactions will be paid for
    // by this account and be signed by this key
    client1.setOperator(getOperatorId(), getOperatorKey());

    return (client1);
  }/*createHederaClient*/

  public static byte[] parseHex(String hex) {
    int i, len;
    byte[] data1;

    i = 0;
    len = hex.length();
    data1 = new byte[len / 2];
  
    //noinspection NullableProblems
    for (Integer c : (Iterable<Integer>) hex.chars()::iterator) {
        if ((i % 2) == 0) {
            // high nibble
            data1[i / 2] = (byte) (Character.digit(c, 16) << 4);
        } else {
            // low nibble
            data1[i / 2] &= (byte) Character.digit(c, 16);
        }
      i++;
    }/*for loop*/

      return (data1);
  }/*parseHex*/
}/*ExampleHelper*/
