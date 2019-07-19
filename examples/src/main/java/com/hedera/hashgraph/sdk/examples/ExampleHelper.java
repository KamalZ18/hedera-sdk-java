package com.hedera.hashgraph.sdk.examples;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Map;
import java.util.Objects;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ExampleHelper {
  /*constructor ExampleHelper*/
  public ExampleHelper() { }

  private static Dotenv getEnv() {
  // Load configuration from the environment or a $projectRoot/.env file, if present
  // See .env.sample for an example of what it is looking for
        return Dotenv.load();
  }/*getEnv*/

  public static AccountId getNodeId() {
        return AccountId.fromString(Objects.requireNonNull(getEnv().get("NODE_ID")));
  }/*getNodeId*/

  public static AccountId getOperatorId() {
        return AccountId.fromString(Objects.requireNonNull(getEnv().get("OPERATOR_ID")));
  }/*getOperatorId*/

  public static Ed25519PrivateKey getOperatorKey() {
      return Ed25519PrivateKey.fromString(Objects.requireNonNull(getEnv().get("OPERATOR_KEY")));
  }/*getOperatorKey*/

  public static Client createHederaClient() {
  // To connect to a network with more nodes, add additional entries to the network map
    var nodeAddress = Objects.requireNonNull(getEnv().get("NODE_ADDRESS"));
    var client = new Client(Map.of(getNodeId(), nodeAddress));

    // Defaults the operator account ID and key such that all generated transactions will be paid for
    // by this account and be signed by this key
    client.setOperator(getOperatorId(), getOperatorKey());

    return client;
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
