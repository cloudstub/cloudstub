package io.cloudstub.s3;

/**
 * The S3 state-store key scheme, shared by the AWS-protocol surface ({@link CloudStubS3Service})
 * and the REST/CLI surface ({@link CloudStubS3ApiService}). Keys live under the {@code s3/} prefix:
 *
 * <ul>
 *   <li>{@code s3/buckets/{bucket}} → bucket metadata (marks the bucket's existence)
 *   <li>{@code s3/buckets/{bucket}/objects/{key}} → the object record (body, content type, ETag, …)
 *   <li>{@code s3/tags/objects/{bucket}/{key}} → the object's tag set
 *   <li>{@code s3/tags/buckets/{bucket}} → the bucket's tag set
 * </ul>
 *
 * <p>An object key may itself contain {@code '/'} (S3 keys are flat strings with slash-style
 * prefixes); a bucket name may not, so a key with no further {@code '/'} after {@link
 * #BUCKETS_PREFIX} is a bucket marker and anything under {@code .../objects/} is an object. Tag
 * sets live under a separate {@code s3/tags/} root so they are never iterated as objects by a
 * listing.
 */
final class S3Keys {

    private S3Keys() {}

    static final String BUCKETS_PREFIX = "s3/buckets/";
    static final String OBJECT_TAGS_PREFIX = "s3/tags/objects/";
    static final String BUCKET_TAGS_PREFIX = "s3/tags/buckets/";

    static String bucketKey(String bucket) {
        return BUCKETS_PREFIX + bucket;
    }

    static String objectPrefix(String bucket) {
        return BUCKETS_PREFIX + bucket + "/objects/";
    }

    static String objectKey(String bucket, String key) {
        return objectPrefix(bucket) + key;
    }

    /**
     * A bucket marker key (e.g. {@code s3/buckets/demo}) has no further path segment; object keys
     * do.
     */
    static boolean isBucketMarkerKey(String key) {
        return key.indexOf('/', BUCKETS_PREFIX.length()) < 0;
    }

    /** Recovers the object key from a full object state key, given its bucket. */
    static String objectKeyFromStateKey(String bucket, String stateKey) {
        return stateKey.substring(objectPrefix(bucket).length());
    }

    static String objectTagsKey(String bucket, String key) {
        return OBJECT_TAGS_PREFIX + bucket + "/" + key;
    }

    static String objectTagsPrefix(String bucket) {
        return OBJECT_TAGS_PREFIX + bucket + "/";
    }

    static String bucketTagsKey(String bucket) {
        return BUCKET_TAGS_PREFIX + bucket;
    }
}
