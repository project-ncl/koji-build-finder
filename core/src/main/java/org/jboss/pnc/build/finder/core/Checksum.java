/*
 * Copyright (C) 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.build.finder.core;

import static org.jboss.pnc.build.finder.core.AnsiUtils.red;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.eclipse.packager.rpm.RpmSignatureTag;
import org.eclipse.packager.rpm.RpmTag;
import org.eclipse.packager.rpm.coding.PayloadCoding;
import org.eclipse.packager.rpm.parse.InputHeader;
import org.eclipse.packager.rpm.parse.RpmInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Checksum implements Comparable<Checksum>, Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Checksum.class);

    private static final long serialVersionUID = -7347509034711302799L;

    private static final int BUFFER_SIZE = 1024;

    private ChecksumType type;

    private String value;

    @JsonIgnore
    private String filename;

    @JsonIgnore
    private long fileSize;

    public Checksum() {

    }

    public Checksum(ChecksumType type, String value, String filename, long fileSize) {
        this.type = type;
        this.value = value;
        this.filename = filename;
        this.fileSize = fileSize;
    }

    public Checksum(ChecksumType type, String value, LocalFile localFile) {
        this.type = type;
        this.value = value;
        this.filename = localFile.getFilename();
        this.fileSize = localFile.getSize();
    }

    public static long determineFileSize(FileContent fc) throws FileSystemException {
        try {
            return fc.getSize();
        } catch (FileSystemException e) {
            throw new FileSystemException("Error determining file size. Does file " + fc.getFile() + " exist?", e);
        }
    }

    public static Set<Checksum> checksum(FileObject fo, Collection<ChecksumType> checksumTypes, String root)
            throws IOException {
        Map<ChecksumType, MessageDigest> mds = new EnumMap<>(ChecksumType.class);

        for (ChecksumType checksumType : checksumTypes) {
            try {
                mds.put(checksumType, MessageDigest.getInstance(checksumType.getAlgorithm()));
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        }

        int checksumTypesSize = checksumTypes.size();
        Collection<CompletableFuture<Void>> futures = new ArrayList<>(checksumTypesSize);
        Map<ChecksumType, CompletableFuture<Checksum>> futures2 = new EnumMap<>(ChecksumType.class);
        FileName filename = fo.getName();
        long fileSize;

        if ("rpm".equals(filename.getExtension())) {
            try (FileContent fc = fo.getContent();
                    InputStream is = fc.getInputStream();
                    RpmInputStream in = new RpmInputStream(is)) {
                fileSize = determineFileSize(fc);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Got RPM: {}", filename);

                    InputHeader<RpmTag> payloadHeader = in.getPayloadHeader();
                    Optional<Object> payloadCodingHeader = payloadHeader.getOptionalTag(RpmTag.PAYLOAD_CODING);

                    if (payloadCodingHeader.isPresent()) {
                        String payloadCoding = (String) payloadCodingHeader.get();
                        PayloadCoding coding = PayloadCoding.fromValue(payloadCoding).orElse(PayloadCoding.NONE);

                        LOGGER.debug(
                                "Payload for RPM {} is compressed using: {}",
                                in.getLead().getName(),
                                coding.getValue());
                    }
                }

                for (ChecksumType checksumType : checksumTypes) {
                    LOGGER.debug("Handle checksum type {} for RPM {}", checksumType.getAlgorithm(), filename);

                    CompletableFuture<Checksum> future;

                    switch (checksumType) {
                        case md5:
                            Object md5 = in.getSignatureHeader().getTag(RpmSignatureTag.MD5);

                            if (!(md5 instanceof byte[])) {
                                throw new IOException("Missing " + checksumType.getAlgorithm() + " for " + fo);
                            }

                            String sigmd5 = Hex.encodeHexString((byte[]) md5);

                            future = CompletableFuture.supplyAsync(
                                    () -> new Checksum(checksumType, sigmd5, Utils.normalizePath(fo, root), fileSize));

                            futures2.put(checksumType, future);
                            break;
                        case sha1:
                            Object sha1 = in.getSignatureHeader().getTag(RpmSignatureTag.SHA1HEADER);

                            if (!(sha1 instanceof byte[])) {
                                LOGGER.warn("Missing {} for {}", red(checksumType.getAlgorithm()), red(fo));
                                break;
                            }

                            String sigsha1 = Hex.encodeHexString((byte[]) sha1);

                            future = CompletableFuture.supplyAsync(
                                    () -> new Checksum(checksumType, sigsha1, Utils.normalizePath(fo, root), fileSize));

                            futures2.put(checksumType, future);
                            break;
                        case sha256:
                            Object sha256 = in.getSignatureHeader().getTag(RpmSignatureTag.SHA256HEADER);

                            if (!(sha256 instanceof byte[])) {
                                LOGGER.warn("Missing {} for {}", red(checksumType.getAlgorithm()), red(fo));
                                break;
                            }

                            String sigsha256 = Hex.encodeHexString((byte[]) sha256);

                            future = CompletableFuture.supplyAsync(
                                    () -> new Checksum(
                                            checksumType,
                                            sigsha256,
                                            Utils.normalizePath(fo, root),
                                            fileSize));

                            futures2.put(checksumType, future);
                            break;
                        default:
                            throw new IOException("Unrecognized checksum type: " + checksumType.getAlgorithm());
                    }
                }
            }
        } else {
            try (FileContent fc = fo.getContent(); InputStream is = fc.getInputStream()) {
                fileSize = determineFileSize(fc);
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;

                while ((read = is.read(buffer)) > 0) {
                    int len = read;

                    for (ChecksumType checksumType : checksumTypes) {
                        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                            MessageDigest md = mds.get(checksumType);
                            md.update(buffer, 0, len);
                            return null;
                        });

                        futures.add(future);
                    }

                    for (CompletableFuture<Void> future : futures) {
                        future.join();
                    }

                    futures.clear();
                }
            }

            for (ChecksumType checksumType : checksumTypes) {
                CompletableFuture<Checksum> future = CompletableFuture.supplyAsync(() -> {
                    MessageDigest md = mds.get(checksumType);
                    return new Checksum(
                            checksumType,
                            Hex.encodeHexString(md.digest()),
                            Utils.normalizePath(fo, root),
                            fileSize);
                });

                futures2.put(checksumType, future);
            }
        }

        Set<Checksum> results = new HashSet<>(checksumTypesSize, 1.0f);

        for (ChecksumType checksumType : checksumTypes) {
            try {
                CompletableFuture<Checksum> future = futures2.get(checksumType);

                if (future != null) {
                    results.add(future.get());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new IOException(e);
            }
        }

        return Collections.unmodifiableSet(results);
    }

    static Optional<Checksum> findByType(Collection<Checksum> checksums, ChecksumType type) {
        List<Checksum> list = checksums.stream()
                .filter(checksum -> checksum.getType() == type)
                .collect(Collectors.toUnmodifiableList());
        Checksum checksum = null;

        if (!list.isEmpty()) {
            checksum = list.get(0);
        }

        return Optional.ofNullable(checksum);
    }

    public ChecksumType getType() {
        return type;
    }

    public void setType(ChecksumType type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public int compareTo(Checksum o) {
        if (o == null) {
            return 1;
        }

        int i = Integer.compare(type.ordinal(), o.type.ordinal());

        if (i != 0) {
            return i;
        }

        int j = StringUtils.compare(value, o.value);

        if (j != 0) {
            return j;
        }

        int k = StringUtils.compare(filename, o.filename);

        if (k != 0) {
            return k;
        }

        return Long.compare(fileSize, o.fileSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Checksum checksum = (Checksum) o;
        return fileSize == checksum.fileSize && type == checksum.type && Objects.equals(value, checksum.value)
                && Objects.equals(filename, checksum.filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, filename, fileSize);
    }

    @Override
    public String toString() {
        return "Checksum{" + "type=" + type + ", value='" + value + '\'' + ", filename='" + filename + '\''
                + ", fileSize=" + fileSize + '}';
    }
}
