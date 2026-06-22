package io.cloudstub.example.service;

import java.util.List;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/** Stores and retrieves objects in an S3 bucket. */
@Service
public class ObjectStore {

    private final S3Client s3;
    private final String bucket;

    public ObjectStore(S3Client s3) {
        this.s3 = s3;
        this.bucket = "documents";
    }

    /** Creates the bucket. */
    public void createBucket() {
        s3.createBucket(b -> b.bucket(bucket));
    }

    /** Stores {@code body} under {@code key}. */
    public void put(String key, String body) {
        s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromString(body));
    }

    /** Returns the body stored under {@code key}. */
    public String get(String key) {
        return s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asUtf8String();
    }

    /** Lists the keys currently stored in the bucket. */
    public List<String> list() {
        return s3.listObjectsV2(b -> b.bucket(bucket)).contents().stream()
                .map(S3Object::key)
                .toList();
    }
}
