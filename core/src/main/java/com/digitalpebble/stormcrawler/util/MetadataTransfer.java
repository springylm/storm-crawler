/**
 * Licensed to DigitalPebble Ltd under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * DigitalPebble licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.digitalpebble.stormcrawler.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.digitalpebble.stormcrawler.Metadata;

import clojure.lang.PersistentVector;

/**
 * Implements the logic of how the metadata should be passed to the outlinks,
 * what should be stored back in the persistence layer etc...
 */
public class MetadataTransfer {

    /**
     * Class to use for transfering metadata to outlinks. Must extend the class
     * MetadataTransfer.
     */
    public static final String metadataTransferClassParamName = "metadata.transfer.class";

    /**
     * Parameter name indicating which metadata to transfer to the outlinks and
     * persist for a given document. Value is either a vector or a single valued
     * String.
     */
    public static final String metadataTransferParamName = "metadata.transfer";

    /**
     * Parameter name indicating which metadata to persist for a given document
     * but <b>not</b> transfer to outlinks. Value is either a vector or a single
     * valued String.
     */
    public static final String metadataPersistParamName = "metadata.persist";

    /**
     * Parameter name indicating whether to track the url path or not. Boolean
     * value, true by default.
     */
    public static final String trackPathParamName = "metadata.track.path";

    /**
     * Parameter name indicating whether to track the depth from seed. Boolean
     * value, true by default.
     */
    public static final String trackDepthParamName = "metadata.track.depth";

    /** Metadata key name for tracking the source URLs */
    public static final String urlPathKeyName = "url.path";

    /** Metadata key name for tracking the depth */
    public static final String depthKeyName = "depth";

    private Set<String> mdToTransfer = new HashSet<>();

    private Set<String> mdToPersistOnly = new HashSet<>();

    private boolean trackPath = true;

    private boolean trackDepth = true;

    public static MetadataTransfer getInstance(Map<String, Object> conf) {
        String className = ConfUtils.getString(conf,
                metadataTransferClassParamName);

        MetadataTransfer transferInstance;

        // no custom class specified
        if (StringUtils.isBlank(className)) {
            transferInstance = new MetadataTransfer();
        }

        else {
            try {
                Class<?> transferClass = Class.forName(className);
                boolean interfaceOK = MetadataTransfer.class
                        .isAssignableFrom(transferClass);
                if (!interfaceOK) {
                    throw new RuntimeException("Class " + className
                            + " must extend MetadataTransfer");
                }
                transferInstance = (MetadataTransfer) transferClass
                        .newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Can't instanciate " + className);
            }
        }

        // should not be null
        if (transferInstance != null)
            transferInstance.configure(conf);

        return transferInstance;
    }

    protected void configure(Map<String, Object> conf) {

        trackPath = ConfUtils.getBoolean(conf, trackPathParamName, true);

        trackDepth = ConfUtils.getBoolean(conf, trackDepthParamName, true);

        // keep the path but don't add anything to it
        if (trackPath) {
            mdToTransfer.add(urlPathKeyName);
        }

        // keep the depth but don't add anything to it
        if (trackDepth) {
            mdToTransfer.add(depthKeyName);
        }

        Object obj = conf.get(metadataTransferParamName);
        if (obj != null) {
            if (obj instanceof PersistentVector) {
                mdToTransfer.addAll((PersistentVector) obj);
            }
            // single value?
            else {
                mdToTransfer.add(obj.toString());
            }
        }

        obj = conf.get(metadataPersistParamName);
        if (obj != null) {
            if (obj instanceof PersistentVector) {
                mdToPersistOnly.addAll((PersistentVector) obj);
            }
            // single value?
            else {
                mdToPersistOnly.add(obj.toString());
            }
        }
    }

    /**
     * Determine which metadata should be transfered to an outlink. Adds
     * additional metadata like the URL path.
     **/
    public Metadata getMetaForOutlink(String targetURL, String sourceURL,
            Metadata parentMD) {
        Metadata md = _filter(parentMD, mdToTransfer);

        // keep the path?
        if (trackPath) {
            md.addValue(urlPathKeyName, sourceURL);
        }

        // track depth
        if (trackDepth) {
            String existingDepth = md.getFirstValue(depthKeyName);
            int depth;
            try {
                depth = Integer.parseInt(existingDepth);
            } catch (Exception e) {
                depth = 0;
            }
            md.setValue(depthKeyName, Integer.toString(++depth));
        }

        return md;
    }

    /**
     * Determine which metadata should be persisted for a given document
     * including those which are not necessarily transferred to the outlinks
     **/
    public Metadata filter(Metadata metadata) {
        Metadata filtered_md = _filter(metadata, mdToTransfer);

        // add the features that are only persisted but
        // not transfered like __redirTo_
        filtered_md.putAll(_filter(metadata, mdToPersistOnly));

        return filtered_md;
    }

    private Metadata _filter(Metadata metadata, Set<String> filter) {
        Metadata filtered_md = new Metadata();
        for (String key : filter) {
            String[] vals = metadata.getValues(key);
            if (vals != null)
                filtered_md.setValues(key, vals);
        }
        return filtered_md;
    }

}
