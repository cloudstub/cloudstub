package io.cloudstub.lambda;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Lambda path/identifier helpers. Dependency-free (JDK only). */
final class LambdaHelpers {

    private LambdaHelpers() {}

    static final String ACCOUNT_ID = "000000000000";
    static final String REGION = "us-east-1";

    /**
     * Resolves a path token to a bare function name. Accepts a plain name, a function ARN ({@code
     * arn:aws:lambda:region:acct:function:name}), or a name/ARN with a {@code :qualifier} suffix;
     * the qualifier (version or alias) is dropped, since qualifiers are not simulated. The token is
     * URL-decoded first, so an encoded ARN in a tag path resolves too.
     */
    static String functionName(String token) {
        if (token == null) {
            return null;
        }
        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        int fn = decoded.indexOf(":function:");
        if (fn >= 0) {
            decoded = decoded.substring(fn + ":function:".length());
        }
        int qualifier = decoded.indexOf(':');
        return qualifier >= 0 ? decoded.substring(0, qualifier) : decoded;
    }

    /** The canonical function ARN for a name. */
    static String arn(String name) {
        return "arn:aws:lambda:" + REGION + ":" + ACCOUNT_ID + ":function:" + name;
    }

    /** Base64-encoded SHA-256 of the given bytes, matching Lambda's {@code CodeSha256} format. */
    static String sha256Base64(byte[] bytes) {
        try {
            return Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK is missing SHA-256", e);
        }
    }
}
