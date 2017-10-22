package com.codingchili.ethereumingest.model;

import com.codingchili.core.storage.Storable;
import org.web3j.protocol.core.methods.response.Transaction;

import java.math.BigInteger;

public class EthereumTransaction extends Transaction implements Storable {
    public static final String ONE_MILLION = "1000000";
    private String timestamp;

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String id() {
        return getHash();
    }

    @Override
    public BigInteger getNonce() {
        return bigFromNumericString(getNonceRaw());
    }

    @Override
    public BigInteger getBlockNumber() {
        return bigFromNumericString(getBlockNumberRaw());
    }

    @Override
    public BigInteger getTransactionIndex() {
        return bigFromNumericString(getTransactionIndexRaw());
    }

    @Override
    public BigInteger getGas() {
        return bigFromNumericString(getGasRaw());
    }

    @Override
    public BigInteger getGasPrice() {
        return bigFromNumericString(getGasPriceRaw());
    }

    @Override
    public BigInteger getValue() {
        return bigFromNumericString(getValueRaw());
    }

    private static BigInteger bigFromNumericString(String raw) {
        BigInteger result = new BigInteger("0");

        if (raw != null && !raw.isEmpty()) {
            if (raw.contains("x")) {
                result = new BigInteger(raw.substring(2), 16);
            } else {
                try {
                    result = BigInteger.valueOf(Long.parseLong(raw));
                } catch (NumberFormatException e) {
                    // default to 0.
                }
            }
        }
        return BigInteger.valueOf(result.longValue());
    }
}