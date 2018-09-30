/*
 * Copyright (C) 2018 The Android Open Source Project
 *
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
 * limitations under the License
 */

package com.android.server.backup.encryption.chunk;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import com.android.internal.util.Preconditions;
import com.android.server.testing.FrameworkRobolectricTestRunner;
import com.android.server.testing.SystemLoaderPackages;
import com.google.common.base.Charsets;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(FrameworkRobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 26)
// Include android.util.proto in addition to classes under test because the latest versions of
// android.util.proto.Proto{Input|Output}Stream are not part of Robolectric.
@SystemLoaderPackages({"com.android.server.backup", "android.util.proto"})
@Presubmit
public class ChunkListingTest {
    private static final String CHUNK_A = "CHUNK_A";
    private static final String CHUNK_B = "CHUNK_B";
    private static final String CHUNK_C = "CHUNK_C";

    private static final int CHUNK_A_LENGTH = 256;
    private static final int CHUNK_B_LENGTH = 1024;
    private static final int CHUNK_C_LENGTH = 4055;

    private ChunkHash mChunkHashA;
    private ChunkHash mChunkHashB;
    private ChunkHash mChunkHashC;

    @Before
    public void setUp() throws Exception {
        mChunkHashA = getHash(CHUNK_A);
        mChunkHashB = getHash(CHUNK_B);
        mChunkHashC = getHash(CHUNK_C);
    }

    @Test
    public void testHasChunk_whenChunkInListing_returnsTrue() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB, mChunkHashC},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH, CHUNK_C_LENGTH});
        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));

        boolean chunkAInList = chunkListing.hasChunk(mChunkHashA);
        boolean chunkBInList = chunkListing.hasChunk(mChunkHashB);
        boolean chunkCInList = chunkListing.hasChunk(mChunkHashC);

        assertThat(chunkAInList).isTrue();
        assertThat(chunkBInList).isTrue();
        assertThat(chunkCInList).isTrue();
    }

    @Test
    public void testHasChunk_whenChunkNotInListing_returnsFalse() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH});
        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));
        ChunkHash chunkHashEmpty = getHash("");

        boolean chunkCInList = chunkListing.hasChunk(mChunkHashC);
        boolean emptyChunkInList = chunkListing.hasChunk(chunkHashEmpty);

        assertThat(chunkCInList).isFalse();
        assertThat(emptyChunkInList).isFalse();
    }

    @Test
    public void testGetChunkEntry_returnsEntryWithCorrectLength() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB, mChunkHashC},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH, CHUNK_C_LENGTH});
        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));

        ChunkListing.Entry entryA = chunkListing.getChunkEntry(mChunkHashA);
        ChunkListing.Entry entryB = chunkListing.getChunkEntry(mChunkHashB);
        ChunkListing.Entry entryC = chunkListing.getChunkEntry(mChunkHashC);

        assertThat(entryA.getLength()).isEqualTo(CHUNK_A_LENGTH);
        assertThat(entryB.getLength()).isEqualTo(CHUNK_B_LENGTH);
        assertThat(entryC.getLength()).isEqualTo(CHUNK_C_LENGTH);
    }

    @Test
    public void testGetChunkEntry_returnsEntryWithCorrectStart() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB, mChunkHashC},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH, CHUNK_C_LENGTH});
        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));

        ChunkListing.Entry entryA = chunkListing.getChunkEntry(mChunkHashA);
        ChunkListing.Entry entryB = chunkListing.getChunkEntry(mChunkHashB);
        ChunkListing.Entry entryC = chunkListing.getChunkEntry(mChunkHashC);

        assertThat(entryA.getStart()).isEqualTo(0);
        assertThat(entryB.getStart()).isEqualTo(CHUNK_A_LENGTH);
        assertThat(entryC.getStart()).isEqualTo(CHUNK_A_LENGTH + CHUNK_B_LENGTH);
    }

    @Test
    public void testGetChunkEntry_returnsNullForNonExistentChunk() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH});
        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));

        ChunkListing.Entry chunkEntryNonexistentChunk = chunkListing.getChunkEntry(mChunkHashC);

        assertThat(chunkEntryNonexistentChunk).isNull();
    }

    @Test
    public void testReadFromProto_whenEmptyProto_returnsChunkListingWith0Chunks() throws Exception {
        ProtoInputStream emptyProto = new ProtoInputStream(new ByteArrayInputStream(new byte[] {}));

        ChunkListing chunkListing = ChunkListing.readFromProto(emptyProto);

        assertThat(chunkListing.getChunkCount()).isEqualTo(0);
    }

    @Test
    public void testReadFromProto_returnsChunkListingWithCorrectSize() throws Exception {
        byte[] chunkListingProto =
                createChunkListingProto(
                        new ChunkHash[] {mChunkHashA, mChunkHashB, mChunkHashC},
                        new int[] {CHUNK_A_LENGTH, CHUNK_B_LENGTH, CHUNK_C_LENGTH});

        ChunkListing chunkListing =
                ChunkListing.readFromProto(
                        new ProtoInputStream(new ByteArrayInputStream(chunkListingProto)));

        assertThat(chunkListing.getChunkCount()).isEqualTo(3);
    }

    private byte[] createChunkListingProto(ChunkHash[] hashes, int[] lengths) {
        Preconditions.checkArgument(hashes.length == lengths.length);
        ProtoOutputStream outputStream = new ProtoOutputStream();

        for (int i = 0; i < hashes.length; ++i) {
            writeToProtoOutputStream(outputStream, hashes[i], lengths[i]);
        }
        outputStream.flush();

        return outputStream.getBytes();
    }

    private void writeToProtoOutputStream(ProtoOutputStream out, ChunkHash chunkHash, int length) {
        long token = out.start(ChunksMetadataProto.ChunkListing.CHUNKS);
        out.write(ChunksMetadataProto.Chunk.HASH, chunkHash.getHash());
        out.write(ChunksMetadataProto.Chunk.LENGTH, length);
        out.end(token);
    }

    private ChunkHash getHash(String name) {
        return new ChunkHash(
                Arrays.copyOf(name.getBytes(Charsets.UTF_8), ChunkHash.HASH_LENGTH_BYTES));
    }
}
