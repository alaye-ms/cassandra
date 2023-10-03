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

package org.apache.cassandra.index.sai.disk.v1.segment;

import java.io.IOException;

import org.apache.cassandra.db.ReadCommand;
import org.apache.cassandra.db.partitions.PartitionIterator;
import org.apache.cassandra.index.sai.QueryContext;
import org.apache.cassandra.index.sai.iterators.KeyRangeIterator;
import org.apache.cassandra.index.sai.plan.Expression;
import org.apache.cassandra.index.sai.postings.PeekablePostingList;

/**
 * There are two steps in ordering:
 * <p>
 * 1. Limit a single sstable's results to the correct keys. At this stage
 *    we put them back in primary key order to play nice with the rest of
 *    the query pipeline.
 * 2. Merge the results from multiple sstables. Now we leave them in the
 *    final, correct order.
 * <p>
 * SegmentOrdering handles the first step.
 *
 * The second step is handled during post-reconciliation processing when the
 * {@link org.apache.cassandra.index.Index.QueryPlan#postProcessor(ReadCommand)} is called by
 * {@link org.apache.cassandra.db.PartitionRangeReadCommand#postReconciliationProcessing(PartitionIterator)}. If the
 * {@link org.apache.cassandra.index.Index.QueryPlan} is for a topK query then a reordering filter is returned to return
 * the partitions in the correct order.
 */
public interface SegmentOrdering
{
    /**
     * Reorder, limit, and put back into original order the results from a single sstable
     */
    default KeyRangeIterator limitToTopKResults(QueryContext context, PeekablePostingList iterator, Expression exp) throws IOException
    {
        throw new UnsupportedOperationException();
    }
}