package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.crypto.signature.ECDSASignatureResult;
import org.fisco.bcos.sdk.v3.crypto.signature.SignatureResult;
import org.fisco.bcos.sdk.v3.transaction.signer.RemoteSignCallbackInterface;
import org.fisco.bcos.sdk.v3.transaction.signer.RemoteSignProviderInterface;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.springframework.stereotype.Component;

import java.util.*;

/* SDK签名请求发送给Webase-Sign */
@Component
public class RemoteSignProvider implements RemoteSignProviderInterface {
    private final SignClient signClient;
    private final ThreadLocal<String> currentUser = new ThreadLocal<>();

    // 注入HTTP签名客户端
    public RemoteSignProvider(SignClient signClient) {
        this.signClient = signClient;
    }

    // 设置签名身份
    public void setCurrentUser(String signUserId) {
        this.currentUser.set(signUserId);
    }

    // 清除旧签名身份
    public void clearCurrentUser() {
        this.currentUser.remove();
    }

    // SDK签名回调函数，传入交易哈希，返回签名
    @Override
    public SignatureResult requestForSign(byte[] messageHash, int cryptoType) {
        String user = currentUser.get();
        if (user == null) {
            throw new IllegalStateException("未设置签名用户（signUserId）");
        }
        String sigHex = signClient.signMessageHash(user, Hex.toHexStringWithPrefix(messageHash));
        byte[] sig = Numeric.hexStringToByteArray(sigHex);  // 65字节
        return new ECDSASignatureResult(sig[0],
                Arrays.copyOfRange(sig,1,33),
                Arrays.copyOfRange(sig,33,65));
    }

    // 异步入口（同步）
    @Override
    public void requestForSignAsync(byte[] messageHash, int cryptoType,
                               RemoteSignCallbackInterface callback) {
        callback.handleSignedTransaction(requestForSign(messageHash, cryptoType));
    }
}
