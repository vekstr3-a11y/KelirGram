package com.exteragram.messenger.boost.encryption;

import org.telegram.messenger.MessageObject;

public class EncryptionHelper {

    public static final String[] names = new String[]{ "None" };

    public interface EncryptCallback {
        void onResult(String message);
    }

    public static boolean isEncrypted(String message) {
        return false;
    }

    public static boolean isEncrypted(MessageObject messageObject, MessageObject.GroupedMessages group) {
        return false;
    }

    public static MessageObject decryptMessage(MessageObject messageObject) {
        return messageObject;
    }

    public static MessageObject decryptMessage(MessageObject messageObject, MessageObject.GroupedMessages group) {
        return messageObject;
    }

    public static BaseEncryptor getEncryptorBy(long dialogId) {
        return new BaseEncryptor();
    }

    public static int getEncryptorTypeFor(long dialogId) {
        return 0;
    }

    public static void setEncryptorTypeFor(long dialogId, int type) {
    }

    public static void encryptMessage(String text, long dialogId, int type, EncryptCallback callback) {
        if (callback != null) {
            callback.onResult(text);
        }
    }

    public static String encryptMessage(String text, long dialogId, BaseEncryptor encryptor) {
        return text;
    }
}
