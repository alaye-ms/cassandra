/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.index.sai;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import io.github.jbellis.jvector.util.Bits;
import org.apache.cassandra.index.sai.disk.PrimaryKeyMap;
import org.apache.cassandra.index.sai.disk.v1.segment.SegmentMetadata;
import org.apache.cassandra.index.sai.disk.v1.vector.CassandraDiskAnn;
import org.apache.cassandra.index.sai.disk.v1.vector.CassandraOnHeapGraph;
import org.apache.cassandra.index.sai.utils.PrimaryKey;

public class VectorContext
{
    private TreeSet<PrimaryKey> shadowedPrimaryKeys; // allocate when needed

    public void recordShadowedPrimaryKey(PrimaryKey primaryKey)
    {
        if (shadowedPrimaryKeys == null)
            shadowedPrimaryKeys = new TreeSet<>();
        shadowedPrimaryKeys.add(primaryKey);
    }

    // Returns true if the row ID will be included or false if the row ID will be shadowed
    public boolean shouldInclude(long sstableRowId, PrimaryKeyMap primaryKeyMap)
    {
        return shadowedPrimaryKeys == null || !shadowedPrimaryKeys.contains(primaryKeyMap.primaryKeyFromRowId(sstableRowId));
    }

    public boolean containsShadowedPrimaryKey(PrimaryKey primaryKey)
    {
        return shadowedPrimaryKeys != null && shadowedPrimaryKeys.contains(primaryKey);
    }

    /**
     * @return shadowed primary keys, in ascending order
     */
    public NavigableSet<PrimaryKey> getShadowedPrimaryKeys()
    {
        if (shadowedPrimaryKeys == null)
            return Collections.emptyNavigableSet();
        return shadowedPrimaryKeys;
    }

    public Bits bitsetForShadowedPrimaryKeys(CassandraOnHeapGraph<PrimaryKey> graph)
    {
        if (shadowedPrimaryKeys == null)
            return null;

        return new IgnoredKeysBits(graph, shadowedPrimaryKeys);
    }

    public Bits bitsetForShadowedPrimaryKeys(SegmentMetadata metadata, PrimaryKeyMap primaryKeyMap, CassandraDiskAnn graph) throws IOException
    {
        Set<Integer> ignoredOrdinals = null;
        try (var ordinalsView = graph.getOrdinalsView())
        {
            for (PrimaryKey primaryKey : getShadowedPrimaryKeys())
            {
                // not in current segment
                if (primaryKey.compareTo(metadata.minKey) < 0 || primaryKey.compareTo(metadata.maxKey) > 0)
                    continue;

                long sstableRowId = primaryKeyMap.rowIdFromPrimaryKey(primaryKey);
                if (sstableRowId == Long.MAX_VALUE) // not found
                    continue;

                int segmentRowId = Math.toIntExact(sstableRowId - metadata.rowIdOffset);
                // not in segment yet
                if (segmentRowId < 0)
                    continue;
                // end of segment
                if (segmentRowId > metadata.maxSSTableRowId)
                    break;

                int ordinal = ordinalsView.getOrdinalForRowId(segmentRowId);
                if (ordinal >= 0)
                {
                    if (ignoredOrdinals == null)
                        ignoredOrdinals = new HashSet<>();
                    ignoredOrdinals.add(ordinal);
                }
            }
        }

        if (ignoredOrdinals == null)
            return null;

        return new IgnoringBits(ignoredOrdinals, metadata);
    }

    private static class IgnoringBits implements Bits
    {
        private final Set<Integer> ignoredOrdinals;
        private final int length;

        public IgnoringBits(Set<Integer> ignoredOrdinals, SegmentMetadata metadata)
        {
            this.ignoredOrdinals = ignoredOrdinals;
            this.length = 1 + Math.toIntExact(metadata.maxSSTableRowId - metadata.rowIdOffset);
        }

        @Override
        public boolean get(int index)
        {
            return !ignoredOrdinals.contains(index);
        }

        @Override
        public int length()
        {
            return length;
        }
    }

    private static class IgnoredKeysBits implements Bits
    {
        private final CassandraOnHeapGraph<PrimaryKey> graph;
        private final NavigableSet<PrimaryKey> ignored;

        public IgnoredKeysBits(CassandraOnHeapGraph<PrimaryKey> graph, NavigableSet<PrimaryKey> ignored)
        {
            this.graph = graph;
            this.ignored = ignored;
        }

        @Override
        public boolean get(int ordinal)
        {
            var keys = graph.keysFromOrdinal(ordinal);
            return keys.stream().anyMatch(k -> !ignored.contains(k));
        }

        @Override
        public int length()
        {
            return graph.size();
        }
    }
}
