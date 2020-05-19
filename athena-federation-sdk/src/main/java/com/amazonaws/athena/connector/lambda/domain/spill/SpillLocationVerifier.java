package com.amazonaws.athena.connector.lambda.domain.spill;

/*-
 * #%L
 * Amazon Athena Query Federation SDK
 * %%
 * Copyright (C) 2019 - 2020 Amazon Web Services
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is used to track the bucket and its state, and check its validity
 */
public class SpillLocationVerifier
{
    private static final Logger logger = LoggerFactory.getLogger(SpillLocationVerifier.class);

    private enum BucketState
    {UNCHECKED, VALID, INVALID}

    private String bucket;
    private BucketState state;

    /**
     * @param bucket The name of the spill bucket.
     */
    public SpillLocationVerifier(String bucket)
    {
        this.bucket = bucket;
        this.state = BucketState.UNCHECKED;
    }

    /**
     * Public function used to check if the account that calls the lambda function owns the spill bucket
     *
     * @param spillBucket The name of the spill bucket.
     */
    public void checkBucketAuthZ(String spillBucket)
    {
        if (!bucket.equals(spillBucket)) {
            logger.debug("Spill bucket has been changed from " + bucket + " to " + spillBucket);
            bucket = spillBucket;
            state = BucketState.UNCHECKED;
        }

        if (state == BucketState.UNCHECKED) {
            updateBucketState();
        }

        passOrFail();
    }

    /**
     * Helper function to check bucket ownership if it hasn't been checked before
     *
     */
    @VisibleForTesting
    void updateBucketState()
    {
        AmazonS3 s3 = getS3();
        Set<String> buckets = s3.listBuckets().stream().map(b -> b.getName()).collect(Collectors.toSet());

        if (!buckets.contains(bucket)) {
            state = BucketState.INVALID;
        }
        else {
            state = BucketState.VALID;
        }

        logger.debug("The state of bucket " + bucket + " has been updated to " + state + " from " + BucketState.UNCHECKED);
    }

    /**
     * Helper function to throw exception if lambda function doesn't own the spill bucket
     *
     */
    @VisibleForTesting
    void passOrFail()
    {
        if (state == BucketState.UNCHECKED) {
            throw new RuntimeException("Bucket state should have been checked already.");
        }
        else if (state == BucketState.INVALID) {
            throw new RuntimeException("You do NOT own the spill bucket with the name: " + bucket);
        }
        else {
            // no op
        }
    }

    /**
     * Helper function to get the AmazonS3 instance of the lambda
     */
    @VisibleForTesting
    AmazonS3 getS3()
    {
        return AmazonS3ClientBuilder.standard().build();
    }
}