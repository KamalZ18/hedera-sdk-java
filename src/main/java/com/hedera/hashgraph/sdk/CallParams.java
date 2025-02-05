package com.hedera.hashgraph.sdk;

import com.google.protobuf.ByteString;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.util.encoders.DecoderException;
import org.bouncycastle.util.encoders.Hex;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnegative;
import javax.annotation.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import static com.hedera.hashgraph.sdk.SolidityUtil.checkAddressLen;
import static com.hedera.hashgraph.sdk.SolidityUtil.decodeAddress;
import static java.nio.charset.StandardCharsets.US_ASCII;

// an implementation of function selector and parameter encoding as specified here:
// https://solidity.readthedocs.io/en/v0.5.7/abi-spec.html#
public final class CallParams<Kind> {

    @Nullable
    private final FunctionSelector funcSelector;
    private final ArrayList<Argument> args = new ArrayList<>();

    private CallParams(@Nullable FunctionSelector funcSelector) {
        this.funcSelector = funcSelector;
    }

    /**Create a new function call builder for a smart contract constructor.*/
    public static CallParams<Constructor> constructor() {
        return new CallParams<>(null);
    }/*CallParams constructor*/

    /**
     * Create a new function call builder for a smart contract function.
     *
     * @param funcName the name of the function without the parameter list.
     */
    public static CallParams<Function> function(String funcName) {
        return new CallParams<>(new FunctionSelector(funcName));
    }

    private void addParamType(String paramType) {
        // we only calculate the selector for functions, not constructors
        if (funcSelector != null) {
            funcSelector.addParamType(paramType);
        }
    }/*addParamType*/

    /**Add a parameter of type {@code string}.
     * <p>
     * For Solidity addresses, use {@link #addAddress(byte[])}.
     * @return {@code this} for fluent usage*/
    public CallParams<Kind> addString(String param) {
        addParamType("string");
        args.add(new Argument(encodeString(param), true));

        return this;
    }/*addString*/

    private static ByteString encodeString(String string) {
      final ByteString strBytes;
      strBytes = ByteString.copyFromUtf8(string);
      // prepend the size of the string in UTF-8 bytes
      return (int256(strBytes.size(), 32).concat(rightPad32(strBytes)));
    }/*encodeString*/

    private static ByteString encodeBytes(byte[] bytes) {
        return int256(bytes.length, 32)
            .concat(rightPad32(ByteString.copyFrom(bytes)));
    }/*encodeBytes*/

    private static void checkFixedArrayLen(int fixedLen, Object array) {
        final int len = Array.getLength(array);

        if (fixedLen != len) {
            throw new IllegalArgumentException(
                "fixedLen (" + fixedLen + ") does not match string length (" + len + ")");
        }
    }/*checkFixedArrayLen*/

    private static ByteString encodeArray(Stream<ByteString> elements, boolean prependLen) {
      final List<ByteString> byteStrList1;
      ByteString strBytesRetVal1;
      
      if (prependLen) {
         byteStrList1 = elements.collect(Collectors.toList());
         strBytesRetVal1 = int256(byteStrList1.size(), 32).concat(ByteString.copyFrom(byteStrList1));
      } else {
            strBytesRetVal1 = ByteString.copyFrom(elements::iterator);
      }
      return (strBytesRetVal1);
    }/*encodeArray*/

    private static ByteString encodeDynArr(List<ByteString> elements, boolean prependLen) {
      final List<ByteString> offsets1;
      final int offsetsLen;
  	  ByteString byteStrElm1;
      long currOffset;
	    int elementSize1, idx;

      elementSize1 = elements.size();
      offsetsLen = elements.size() + (prependLen ? 1 : 0);

      offsets1 = new ArrayList<>(offsetsLen);

      if (prependLen) {
          offsets1.add(int256(elements.size(), 32));
      }

      //currOffset points to start of dynamic segment
      currOffset = offsetsLen * 32;

      for (idx=0; idx<elementSize1; idx++) {
  	      byteStrElm1 = elements.get(idx);
          offsets1.add(int256(currOffset, 64));
          currOffset += byteStrElm1.size();
      }/*for loop*/

      return (ByteString.copyFrom(offsets).concat(ByteString.copyFrom(elements)));
    }/*encodeDynArr*/

    /** Add a parameter of type {@code string[]}.
     * @throws NullPointerException if any value in `strings` is null*/
    public CallParams<Kind> addStringArray(String[] strings) {
	    final ByteString argBytes1;
      List<ByteString> byteStrings1;

      byteStrings1 = null;
      byteStrings1 = Arrays.stream(strings).map(CallParams::encodeString).collect(Collectors.toList());

      argBytes1 = encodeDynArr(byteStrings1, true);

      addParamType("string[]");
      args.add(new Argument(argBytes1, true));

      return this;
    }/*addStringArray*/

    /** Add a parameter of type {@code string[N]}, a fixed-length array of strings.
     * @param fixedLen the length of the fixed-size array type.
     * @throws IllegalArgumentException if {@code fixedLen != strings.length}
     * @throws NullPointerException     if any value in `strings` is null*/
    public CallParams<Kind> addStringArray(String[] strings, int fixedLen) {
	    final ByteString argBytes1;
      List<ByteString> byteStrings1;

      checkFixedArrayLen(fixedLen, strings);

      byteStrings1 = Arrays.stream(strings).map(CallParams::encodeString).collect(Collectors.toList());

      argBytes1 = encodeDynArr(byteStrings1, false);

      addParamType("string[" + fixedLen + "]");
      // argument is dynamic in that the encoded size is not fixed
      args.add(new Argument(argBytes1, true));

      return this;
    }/*addStringArray*/

    /**Add a parameter of type {@code bytes}, a byte-string.*/
    public CallParams<Kind> addBytes(byte[] param) {
        addParamType("bytes");
        args.add(new Argument(encodeBytes(param), true));
        return this;
    }/*addBytes*/

    /**Add a parameter of type {@code bytesN}, a fixed-length byte-string.
     * <p>
     * Only strings up to 32 bytes are permitted.
     * @throws IllegalArgumentException if {@code param.length != fixedLen}
     *                                  or if {@code fixedLen > 32}*/
    public CallParams<Kind> addBytes(byte[] param, int fixedLen) {
      checkFixedArrayLen(fixedLen, param);
      if (fixedLen > 32) {
          throw new IllegalArgumentException(
              "bytesN cannot have a length greater than 32; given length: " + fixedLen);
      }

      addParamType("bytes[" + fixedLen + "]");
      // the bytesN type is fixed size
      args.add(new Argument(rightPad32(ByteString.copyFrom(param)), false));

      return this;
    }/*addBytes*/

    /** Add a parameter of type {@code bytes[]}, an array of byte-strings.*/
    public CallParams<Kind> addBytesArray(byte[][] param) {
      List<ByteString> byteArrays1;

      byteArrays1 = Arrays.stream(param).map(CallParams::encodeBytes).collect(Collectors.toList());

      addParamType("bytes[]");
      args.add(new Argument(encodeDynArr(byteArrays1, true), true));

      return this;
    }/*addBytesArray*/

    /** Add a parameter of type {@code bytes[N]}, a fixed-length array of byte-strings.*/
    public CallParams<Kind> addBytesArray(byte[][] param, int fixedLen) {
	    final ByteString argBytes1;
      List<ByteString> byteStrings1;

      checkFixedArrayLen(fixedLen, param);

      byteStrings1 = Arrays.stream(param).map(CallParams::encodeBytes).collect(Collectors.toList());

      argBytes1 = encodeDynArr(byteStrings1, false);

      addParamType("bytes[" + fixedLen + "]");
      args.add(new Argument(argBytes1, true));

      return this;
    }/*addBytesArray*/

    public CallParams<Kind> addBool(boolean bool) {
        addParamType("bool");
        // boolean encodes to `uint8` of values [0, 1]
        args.add(new Argument(int256(bool ? 1 : 0, 8), false));
        return this;
    }/*addBool*/

    private static void checkIntWidth(int width) {
        if (width % 8 != 0 || width < 8 || width > 256) {
            throw new IllegalArgumentException(
                "Solidity integer width must be a multiple of 8, in the closed range [8, 256]");
        }
    }/*checkIntWidth*/

    private static void checkUnsignedVal(long unsignedVal) {
        if (unsignedVal < 0) {
            throw new IllegalArgumentException("addUint() does not accept negative values");
        }
    }/*checkUnsignedVal*/

    private static void checkBigInt(BigInteger val, int width, boolean signed) {
      final int actualBitLen1;

      checkIntWidth(width);

      // bitLength() does not include the sign bit
      actualBitLen1 = val.bitLength() + (signed ? 1 : 0);

      if (actualBitLen1 > 256) {
          throw new IllegalArgumentException("BigInteger out of range for Solidity integers");
      }

      if (width < actualBitLen1) {
          throw new IllegalArgumentException(
              "BigInteger.bitLength() is greater than the nominal parameter width");
      }
    }/*checkBigInt*/

    /** Add an integer as an signed {@code intN} param, explicitly setting the parameter width.
     * <p>
     * The value will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     * @param width the nominal bit width for encoding the integer type in the function selector,
     *              e.g. {@code width = 128} produces a param type of {@code int128};
     *              must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code width} is not in a valid range (see above).*/
    public CallParams<Kind> addInt(long value, int width) {
      checkIntWidth(width);

      addParamType("int" + width);
      args.add(new Argument(int256(value, width), false));
      return this;
    }/*addInt*/

    /** Add an arbitrary precision integer as a signed {@code intN} param, explicitly
     * setting the parameter width.
     * @param width the nominal bit width for encoding the integer type in the function selector,
     *              e.g. {@code width = 128} produces a param type of {@code int128};
     *              must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code bigInt.bitLength() > 255}
     *                                  (max range including the sign bit),
     *                                  {@code width < uint.bitLength()} or {@code width} is not in
     *                                  a valid range (see above).*/
    public CallParams<Kind> addInt(BigInteger bigInt, int width) {
      checkBigInt(bigInt, width, true);

      addParamType("int" + width);
      args.add(new Argument(int256(bigInt), false));
      return this;
    }/*addInt*/

    private static ByteString encodeIntArray(int intWidth, long[] intArray, boolean prependLen) {
	    final ByteString arrayBytes1;

      checkIntWidth(intWidth);

      arrayBytes1 = ByteString.copyFrom(Arrays.stream(intArray).mapToObj(i -> int256(i, intWidth)).collect(Collectors.toList()));

      if (prependLen) {
          return int256(intArray.length, 32).concat(arrayBytes1);
      } else {
          return arrayBytes1;
      }
    }/*encodeIntArray*/

    private static ByteString encodeIntArray(int intWidth, BigInteger[] intArray, boolean prependLen) {
	    final ByteString arrayBytes1;

      checkIntWidth(intWidth);

      arrayBytes1 = ByteString.copyFrom(Arrays.stream(intArray).map(CallParams::int256).collect(Collectors.toList()));

      if (prependLen) {
          return int256(intArray.length, 32).concat(arrayBytes1);
      } else {
          return arrayBytes1;
      }
    }/*encodeIntArray*/

    private static ByteString encodeUintArray(int intWidth, long[] intArray, boolean prependLen) {
      final ByteString arrayBytes1;
      checkIntWidth(intWidth);

      arrayBytes1 = ByteString.copyFrom(
            Arrays.stream(intArray).mapToObj(i -> {
                checkUnsignedVal(i);
                return int256(i, intWidth);
            }).collect(Collectors.toList()));

      if (prependLen) {
          return int256(intArray.length, 32).concat(arrayBytes1);
      } else {
          return arrayBytes1;
      }
    }/*encodeUintArray*/

    private static ByteString encodeUintArray(int intWidth, BigInteger[] intArray, boolean prependLen) {
	    final ByteString arrayBytes1;
      checkIntWidth(intWidth);

      arrayBytes1 = ByteString.copyFrom(
            Arrays.stream(intArray).map(i -> {
                checkUnsignedVal(i.signum());
                return uint256(i);
            }).collect(Collectors.toList()));

      if (prependLen) {
          return int256(intArray.length, 32).concat(arrayBytes1);
      } else {
          return arrayBytes1;
      }
    }/*encodeUintArray*/

    /**Add an integer array as an signed {@code intN[]} param, explicitly setting the integer
     * bit-width.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code width} is not in a valid range (see above).*/
    public CallParams<Kind> addIntArray(long[] intArray, int intWidth) {
      final ByteString arrayBytes1;

      arrayBytes1 = encodeIntArray(intWidth, intArray, true);
      addParamType("int" + intWidth + "[]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addIntArray*/

    /**Add a fixed-length integer array as a signed {@code intM[N]} param, explicitly setting the
     * integer bit-width and array length.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @param fixedLen the nominal length of the fixed-size array; must be the length of the array
     *                 that is passed.
     * @throws IllegalArgumentException if {@code width} is not in a valid range (see above)
     *                                  or {@code fixedLen != intArray.length}.*/
    public CallParams<Kind> addIntArray(long[] intArray, int intWidth, int fixedLen) {
	    final ByteString arrayBytes1;

      checkFixedArrayLen(fixedLen, intArray);
      arrayBytes1 = encodeIntArray(intWidth, intArray, false);
      addParamType("int" + intWidth + "[" + fixedLen + "]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addIntArray*/

    /**Add an integer array as a signed {@code intN[]} param, explicitly setting the integer
     * bit-width.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code width} is not in a valid range (see above).*/
    public CallParams<Kind> addIntArray(BigInteger[] intArray, int intWidth) {
      final ByteString arrayBytes1;
      
      arrayBytes1 = encodeIntArray(intWidth, intArray, true);
      addParamType("int" + intWidth + "[]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addIntArray*/

    /**Add a fixed-length integer array as a signed {@code intM[N]} param, explicitly setting the
     * integer bit-width and array length.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @param fixedLen the nominal length of the fixed-size array; must be the length of the array
     *                 that is passed.
     * @throws IllegalArgumentException if {@code width} is not in a valid range (see above)
     *                                  or {@code fixedLen != intArray.length}.*/
    public CallParams<Kind> addIntArray(BigInteger[] intArray, int intWidth, int fixedLen) {
      final ByteString arrayBytes1;

      checkFixedArrayLen(fixedLen, intArray);
      arrayBytes1 = encodeIntArray(intWidth, intArray, false);
      addParamType("int" + intWidth + "[" + fixedLen + "]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addIntArray*/

    /**
     * Add a non-negative integer as an unsigned {@code uintN} param,
     * explicitly setting the parameter width.
     * <p>
     * The value will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. Passing a smaller
     * integer type is allowed.
     *
     * @param width the nominal bit width for encoding the integer type in the function selector,
     *              e.g. {@code width = 128} produces a param type of {@code uint128};
     *              must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code uint < 0},
     *                                  or {@code width} is not in a valid range (see above).
     */
    public CallParams<Kind> addUint(@Nonnegative long uint, int width) {
        checkIntWidth(width);
        checkUnsignedVal(uint);

        addParamType("uint" + width);
        args.add(new Argument(int256(uint, width), false));

        return this;
    }/*addUint*/

    /**
     * Add an arbitrary precision non-negative integer as an unsigned {@code uintN} param,
     * explicitly setting the parameter width.
     * <p>
     * As this uses the unsigned type, it gets an extra bit of range over
     * {@link #addInt(BigInteger, int)} which has to count the sign bit.
     *
     * @param width the nominal bit width for encoding the integer type in the function selector,
     *              e.g. {@code width = 128} produces a param type of {@code uint128};
     *              must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if {@code uint.signum() < 0},
     *                                  if {@code uint.bitLength() > 256}
     *                                  (cannot be represented as a Solidity integer type),
     *                                  {@code width < uint.bitLength()} or
     *                                  {@code width} is not in a valid range (see above).
     */
    public CallParams<Kind> addUint(@Nonnegative BigInteger uint, int width) {
        checkBigInt(uint, width, false);
        checkUnsignedVal(uint.signum());

        addParamType("uint" + width);
        args.add(new Argument(uint256(uint), false));

        return this;
    }/*addUint*/

    /**
     * Add an array of non-negative integers as an unsigned {@code intN[]} param, explicitly setting
     * the integer bit-width.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if any value is less than 0,
     *                                  {@code width} is not in a valid range (see above),
     *                                  or {@code fixedLen != intArray.length}.
     */
    public CallParams<Kind> addUintArray(long[] uintArray, int intWidth) {
      final ByteString arrayBytes1;

      arrayBytes1 = encodeUintArray(intWidth, uintArray, true);
      addParamType("uint" + intWidth + "[]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addUintArray*/

    /**Add a fixed-length array of non-negative integers as an unsigned {@code intM[N]} param,
     * explicitly setting the integer bit-width and array length.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @param fixedLen the nominal length of the fixed-size array; must be the length of the array
     *                 that is passed.
     * @throws IllegalArgumentException if any value is less than 0,
     *                                  {@code width} is not in a valid range (see above),
     *                                  or {@code fixedLen != intArray.length}.*/
    public CallParams<Kind> addUintArray(long[] intArray, int intWidth, int fixedLen) {
      final ByteString arrayBytes1;

      checkFixedArrayLen(fixedLen, intArray);
      arrayBytes1 = encodeUintArray(intWidth, intArray, false);
      addParamType("uint" + intWidth + "[" + fixedLen + "]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addUintArray*/

    /**Add an array of arbitrary precision non-negative integers as an unsigned {@code intN[]}
     * param, explicitly setting the integer bit-width.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @throws IllegalArgumentException if any value is less than 0, or has a
     *                                  {@link BigInteger#bitLength()} greater than {@code width},
     *                                  or if {@code width} is not in a valid range (see above).*/
    public CallParams<Kind> addUintArray(BigInteger[] intArray, int intWidth) {
      final ByteString arrayBytes1;

      arrayBytes1 = encodeUintArray(intWidth, intArray, true);
      addParamType("uint" + intWidth + "[]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addUintArray*/

    /**Add a fixed-length array of arbitrary precision non-negative integers as an unsigned
     * {@code intM[N]} param, explicitly setting the integer bit-width and array length.
     * <p>
     * The values will be truncated to the last {@code width} bits, the same as Java's
     * behavior when casting from a larger integer type to a smaller one. When passing a smaller
     * integer type, Java will widen it by sign-extending so if it is truncated again it should
     * still result in the same two's complement value.
     *
     * @param intWidth the nominal bit width for encoding the integer type in the function selector,
     *                 e.g. {@code width = 128} produces a param type of {@code int128};
     *                 must be a multiple of 8 and between 8 and 256.
     * @param fixedLen the nominal length of the fixed-size array; must be the length of the array
     *                 that is passed.
     * @throws IllegalArgumentException if any value is less than 0, or has a
     *                                  {@link BigInteger#bitLength()} greater than {@code width},
     *                                  or if {@code width} is not in a valid range (see above).*/
    public CallParams<Kind> addUintArray(BigInteger[] intArray, int intWidth, int fixedLen) {
      final ByteString arrayBytes1;

      checkFixedArrayLen(fixedLen, intArray);
      arrayBytes1 = encodeUintArray(intWidth, intArray, false);
      addParamType("uint" + intWidth + "[" + fixedLen + "]");
      args.add(new Argument(arrayBytes1, true));
      return this;
    }/*addUintArray*/

    /** The length of a Solidity address in bytes.*/
    public static final int ADDRESS_LEN = SolidityUtil.ADDRESS_LEN;

    /** The length of a hexadecimal-encoded Solidity address, in ASCII characters (bytes).*/
    public static final int ADDRESS_LEN_HEX = SolidityUtil.ADDRESS_LEN_HEX;

    /**
     * Add a {@value ADDRESS_LEN}-byte Solidity address parameter with the type {@code address}.
     * <p>
     * Note: adding a {@code address payable} or {@code contract} parameter must also use
     * this function as the ABI does not support those types directly.
     *
     * @throws IllegalArgumentException if the address is not exactly {@value ADDRESS_LEN} bytes
     *                                  long.
     */
    public CallParams<Kind> addAddress(byte[] address) {
        checkAddressLen(address);

        addParamType("address");
        // address encodes as `uint160`
        args.add(new Argument(leftPad32(ByteString.copyFrom(address)), false));

        return this;
    }/*addAddress*/

    /**Add a {@value ADDRESS_LEN_HEX}-character hex-encoded Solidity address parameter with the type
     * {@code address}.
     * <p>
     * Note: adding a {@code address payable} or {@code contract} parameter must also use
     * this function as the ABI does not support those types directly.
     *
     * @throws IllegalArgumentException if the address is not exactly {@value ADDRESS_LEN_HEX}
     *                                  characters long or fails to decode as hexadecimal.*/
    public CallParams<Kind> addAddress(String address) {
        return addAddress(decodeAddress(address));
    }/*addAddress*/

    /**Add an array of {@value ADDRESS_LEN}-byte Solidity addresses as a {@code address[]} param.
     *
     * @throws IllegalArgumentException if any value is not exactly {@value ADDRESS_LEN} bytes long.
     * @throws NullPointerException     if any value in the array is null.*/
    public CallParams<Kind> addAddressArray(byte[][] addresses) {
	    final ByteString addressArray1;

      addressArray1 = encodeArray(
             Arrays.stream(addresses).map(a -> {
                checkAddressLen(a);
                return leftPad32(ByteString.copyFrom(a));
             }), true);

      addParamType("address[]");
      args.add(new Argument(addressArray1, true));

      return this;
    }/*addAddressArray*/

    /**
     * Add a fixed-length array of {@value ADDRESS_LEN}-byte Solidity addresses as a
     * {@code address[N]} param, explicitly setting the array length.
     *
     * @throws IllegalArgumentException if any value is not exactly {@value ADDRESS_LEN} bytes long
     *                                  or if {@code fixedLen != addresses.length}.
     * @throws NullPointerException     if any value in the array is null.
     */
    public CallParams<Kind> addAddressArray(byte[][] addresses, int fixedLen) {
        final var addressArray = encodeArray(
            Arrays.stream(addresses).map(a -> {
                checkAddressLen(a);
                return leftPad32(ByteString.copyFrom(a));
            }), false);

        addParamType("address[" + fixedLen + "]");
        args.add(new Argument(addressArray, false));

        return this;
    }

    /**
     * Add an array of {@value ADDRESS_LEN_HEX}-character hex-encoded Solidity addresses as a
     * {@code address[]} param.
     *
     * @throws IllegalArgumentException if any value is not exactly {@value ADDRESS_LEN_HEX}
     *                                  characters long or fails to decode as hexadecimal.
     * @throws NullPointerException     if any value in the array is null.
     */
    public CallParams<Kind> addAddressArray(String[] addresses) {
        final var addressArray = encodeArray(
            Arrays.stream(addresses).map(a -> {
                final var address = decodeAddress(a);
                checkAddressLen(address);
                return leftPad32(ByteString.copyFrom(address));
            }), true);

        addParamType("address[]");
        args.add(new Argument(addressArray, true));

        return this;
    }

    /**
     * Add a fixed-length array of {@value ADDRESS_LEN_HEX}-character hex-encoded Solidity addresses
     * as a {@code address[N]} param, explicitly setting the array length.
     *
     * @throws IllegalArgumentException if any value is not exactly {@value ADDRESS_LEN_HEX}
     *                                  characters long, fails to decode as hexadecimal,
     *                                  or if {@code fixedLen != addresses.length}.
     * @throws NullPointerException     if any value in the array is null.
     */
    public CallParams<Kind> addAddressArray(String[] addresses, int fixedLen) {
        final var addressArray = encodeArray(
            Arrays.stream(addresses).map(a -> {
                final var address = decodeAddress(a);
                checkAddressLen(address);
                return leftPad32(ByteString.copyFrom(address));
            }), false);

        addParamType("address[" + fixedLen + "]");
        args.add(new Argument(addressArray, false));

        return this;
    }

    /**
     * Function selector length in bytes
     */
    public static final int SELECTOR_LEN = 4;

    /**
     * Function selector length in hex characters
     */
    public static final int SELECTOR_LEN_HEX = 8;

    /**
     * Add a Solidity function reference as a {@value ADDRESS_LEN}-byte contract address and a
     * {@value SELECTOR_LEN}-byte function selector.
     *
     * @throws IllegalArgumentException if {@code address} is not {@value ADDRESS_LEN} bytes or
     *                                  {@code selector} is not {@value SELECTOR_LEN} bytes.
     */
    public CallParams<Kind> addFunction(byte[] address, byte[] selector) {
        checkAddressLen(address);

        if (selector.length != SELECTOR_LEN) {
            throw new IllegalArgumentException("function selectors must be 4 bytes or 8 hex chars");
        }

        final var output = ByteString.newOutput(ADDRESS_LEN + SELECTOR_LEN);
        output.write(address, 0, address.length);
        output.write(selector, 0, selector.length);

        addParamType("function");
        // function reference encodes as `bytes24`
        args.add(new Argument(rightPad32(output.toByteString()), false));

        return this;
    }

    /**
     * Add a Solidity function reference as a {@value ADDRESS_LEN}-byte contract address and a
     * {@value SELECTOR_LEN}-byte function selector.
     *
     * @param address  a hex-encoded {@value ADDRESS_LEN_HEX}-character Solidity address.
     * @param selector a
     * @throws IllegalArgumentException if {@code address} is not {@value ADDRESS_LEN_HEX}
     *                                  characters or {@code selector} is not
     *                                  {@value SELECTOR_LEN} bytes.
     */
    public CallParams<Kind> addFunction(String address, byte[] selector) {
        return addFunction(decodeAddress(address), selector);
    }

    /**
     * Add a Solidity function reference as a {@value ADDRESS_LEN}-byte contract address and a
     * {@value SELECTOR_LEN_HEX}-character hexadecimal function selector.
     *
     * @param address  a hex-encoded {@value ADDRESS_LEN_HEX}-character Solidity address.
     * @param selector a
     * @throws IllegalArgumentException if {@code address} is not {@value ADDRESS_LEN_HEX}
     *                                  characters or {@code selector} is not
     *                                  {@value SELECTOR_LEN_HEX} characters or fails to decode
     *                                  as hex.
     */
    public CallParams<Kind> addFunction(String address, String selector) {
        if (selector.length() != SELECTOR_LEN_HEX) {
            throw new IllegalArgumentException("function selectors must be 4 bytes or 8 hex chars");
        }

        final byte[] selectorBytes;

        try {
            selectorBytes = Hex.decode(selector);
        } catch (DecoderException e) {
            throw new IllegalArgumentException(
                "failed to decode Solidity function selector as hex", e);
        }

        return addFunction(decodeAddress(address), selectorBytes);
    }

    /**
     * Add a Solidity function reference as a {@value ADDRESS_LEN}-byte contract address and a
     * constructed {@link FunctionSelector}.
     *
     * @return
     * @throws IllegalArgumentException if {@code address} is not {@value ADDRESS_LEN_HEX}
     *                                  characters.
     */
    public CallParams<Kind> addFunction(String address, FunctionSelector selector) {
        // allow the `FunctionSelector` to be reused multiple times
        return addFunction(decodeAddress(address), selector.finishIntermediate());
    }

    /**
     * Get the encoding of the currently added parameters as a {@link ByteString}.
     * <p>
     * You may continue to add parameters and call this again.
     *
     * @return the Solidity encoding of the call parameters in the order they were added.
     */
    public ByteString toProto() {
        // offset for dynamic-length data, immediately after value arguments
        var dynamicOffset = args.size() * 32;

        var paramsBytes = new ArrayList<ByteString>(args.size() + 1);

        if (funcSelector != null) {
            // use `finishIntermediate()` so this object can continue being used
            paramsBytes.add(ByteString.copyFrom(funcSelector.finishIntermediate()));
        }

        var dynamicArgs = new ArrayList<ByteString>();

        // iterate the arguments and determine whether they are dynamic or not
        for (var arg : args) {
            if (arg.isDynamic) {
                // dynamic arguments supply their offset in value position and append their data at
                // that offset
                paramsBytes.add(int256(dynamicOffset, 256));
                dynamicArgs.add(arg.value);
                dynamicOffset += arg.value.size();
            } else {
                // value arguments are dropped in the current arg position
                paramsBytes.add(arg.value);
            }
        }

        paramsBytes.addAll(dynamicArgs);

        return ByteString.copyFrom(paramsBytes);
    }

    // padding that we can substring without new allocations
    private static final ByteString padding = ByteString.copyFrom(new byte[31]);
    private static final ByteString negativePadding;

    static {
        final var fill = new byte[31];
        Arrays.fill(fill, (byte) 0xFF);
        negativePadding = ByteString.copyFrom(fill);
    }

    static ByteString int256(long val, int bitWidth) {
        // don't try to get wider than a `long` as it should just be filled with padding
        bitWidth = Math.min(bitWidth, 64);
        final var output = ByteString.newOutput(bitWidth / 8);

        // write bytes in big-endian order
        for (int i = bitWidth - 8; i >= 0; i -= 8) {
            // widening conversion sign-extends so we don't have to do anything special when
            // truncating a previously widened value
            final var u8 = (byte) (val >> i);
            output.write(u8);
        }

        // byte padding will sign-extend appropriately
        return leftPad32(output.toByteString(), val < 0);
    }

    static ByteString int256(BigInteger bigInt) {
        return leftPad32(bigInt.toByteArray(), bigInt.signum() < 0);
    }

    static ByteString uint256(BigInteger uint) {
        final var bytes = uint.toByteArray();

        final ByteString byteStr;

        if (uint.bitLength() == 256) {
            // cut out the extra byte added by the sign bit so we get full range
            byteStr = ByteString.copyFrom(bytes, 1, bytes.length - 1);
        } else {
            byteStr = ByteString.copyFrom(bytes);
        }

        return leftPad32(byteStr, false);
    }

    // Solidity contracts require all parameters to be padded to 32 byte multiples but specifies
    // different requirements for padding for strings/byte arrays vs integers

    static ByteString leftPad32(ByteString input) {
        return leftPad32(input, false);
    }

    static ByteString leftPad32(ByteString input, boolean negative) {
        var rem = 32 - input.size() % 32;
        return rem == 32
            ? input
            : (negative ? negativePadding : padding).substring(0, rem)
            .concat(input);
    }

    static ByteString leftPad32(byte[] input, boolean negative) {
        return leftPad32(ByteString.copyFrom(input), negative);
    }

    static ByteString rightPad32(ByteString input) {
        var rem = 32 - input.size() % 32;
        return rem == 32 ? input : input.concat(padding.substring(0, rem));
    }

    // some Rust-inspired type magic
    public final static class Constructor {
        private Constructor() {
        }
    }

    public final static class Function {
        private Function() {
        }
    }

    /**
     * Builder class for Solidity function selectors.
     */
    @SuppressFBWarnings(value = {"EI_EXPOSE_REP"},
        justification = "we don't care about the contents of `finished`")
    public static final class FunctionSelector {

        @Nullable
        private Keccak.Digest256 digest;

        private boolean needsComma = false;

        @Nullable
        private byte[] finished = null;

        /**
         * Start building a selector for a function with a given name.
         */
        public FunctionSelector(String funcName) {
            digest = new Keccak.Digest256();
            digest.update(funcName.getBytes(US_ASCII));
            digest.update((byte) '(');
        }

        /**
         * Add a Solidity type name to this selector;
         * {@see https://solidity.readthedocs.io/en/v0.5.9/types.html}
         *
         * @param typeName the name of the Solidity type for a parameter.
         * @return {@code this} for fluent usage.
         * @throws IllegalStateException if {@link #finish()} has already been called.
         */
        public FunctionSelector addParamType(String typeName) {
            if (finished != null) {
                throw new IllegalStateException("FunctionSelector already finished");
            }

            Objects.requireNonNull(digest);

            if (needsComma) {
                digest.update((byte) ',');
            }

            digest.update(typeName.getBytes(US_ASCII));
            needsComma = true;

            return this;
        }

        /**
         * Complete the function selector and return its bytes, but leave the selector in a
         * state which allows adding more parameters.
         * <p>
         * This requires copying the digest state and so is less efficient than {@link #finish()}
         * but is more efficient than throwing the selector state out and starting over
         * with the same subset of parameters.
         *
         * @return the computed selector bytes.
         */
        public byte[] finishIntermediate() {
            if (finished == null) {
                try {
                    final var resetDigest =
                        (Keccak.Digest256) Objects.requireNonNull(digest).clone();
                    final var ret = finish();
                    digest = resetDigest;
                    return ret;
                } catch (CloneNotSupportedException e) {
                    throw new Error("Keccak.Digest256 should implement Cloneable", e);
                }
            }

            return finished;
        }

        /**
         * Complete the function selector after all parameters have been added and get the selector
         * bytes.
         * <p>
         * No more parameters may be added after this method call.
         * If you want to reuse the state of this selector, call {@link #finishIntermediate()}.
         * <p>
         * However, this can be called multiple times; it will always return the same result.
         *
         * @return the computed selector bytes.
         */
        public byte[] finish() {
            if (finished == null) {
                Objects.requireNonNull(digest);
                digest.update((byte) ')');
                finished = Arrays.copyOf(digest.digest(), 4);
                // release digest state
                digest = null;
            }

            return finished;
        }
    }

    private final static class Argument {
        private final ByteString value;
        private final boolean isDynamic;

        private Argument(ByteString value, boolean isDynamic) {
            if (!isDynamic && value.size() != 32) {
                throw new IllegalArgumentException("value argument that was not 32 bytes");
            }

            this.value = value;
            this.isDynamic = isDynamic;
        }
    }
}/*CallParams*/
