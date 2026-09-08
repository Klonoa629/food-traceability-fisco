package com.foodtrace.chain;

import org.fisco.bcos.sdk.v3.crypto.signature.ECDSASignatureResult;
import org.fisco.bcos.sdk.v3.crypto.signature.SignatureResult;
import org.fisco.bcos.sdk.v3.transaction.signer.RemoteSignCallbackInterface;
import org.fisco.bcos.sdk.v3.transaction.signer.RemoteSignProviderInterface;
import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.bcos.sdk.v3.utils.Numeric;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SDK 远程签名提供者
 *
 * <p>实现 java-sdk 的 {@link RemoteSignProviderInterface}，将 SDK 发起的签名请求
 * 转发给 WeBASE-Sign，以当前线程绑定的托管用户身份完成签名。
 */
@Component
public class RemoteSignProvider implements RemoteSignProviderInterface {
    private final SignClient signClient;
    private final ThreadLocal<String> currentUser = new ThreadLocal<>();

    public RemoteSignProvider(SignClient signClient) {
        this.signClient = signClient;
    }

    /**
     * 绑定当前线程的签名身份
     *
     * @param signUserId WeBASE-Sign 托管用户标识
     */
    public void setCurrentUser(String signUserId) {
        this.currentUser.set(signUserId);
    }

    /**
     * 清除当前线程的签名身份
     */
    public void clearCurrentUser() {
        this.currentUser.remove();
    }

    /**
     * 同步签名入口：对交易哈希请求 Sign 托管签名
     *
     * @param messageHash 待签名的交易哈希
     * @param cryptoType  加密类型（0 = ECDSA）
     * @return v、r、s 组装成的 ECDSA 签名结果
     */
    @Override
    public SignatureResult requestForSign(byte[] messageHash, int cryptoType) {
        String user = currentUser.get();
        if (user == null) {
            throw new IllegalStateException("未设置签名用户（signUserId）");
        }
        String sigHex = signClient.signMessageHash(user, Hex.toHexStringWithPrefix(messageHash));
        byte[] sig = Numeric.hexStringToByteArray(sigHex);
        return new ECDSASignatureResult(sig[0],
                Arrays.copyOfRange(sig,1,33),
                Arrays.copyOfRange(sig,33,65));
    }

    // 异步入口：同步执行后回调
    @Override
    public void requestForSignAsync(byte[] messageHash, int cryptoType,
                               RemoteSignCallbackInterface callback) {
        callback.handleSignedTransaction(requestForSign(messageHash, cryptoType));
    }
}
