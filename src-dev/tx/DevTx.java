/**
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

package tx;


public class DevTx
{
    // 1-- Dataset-based API for transactions.
    // 2-- Compatibility/migration?
    // 3-- Events per txn
    
    // --------
    // * Version per dataset
    //   Can flush changes quietly when all up to X are done.
    //   Will see in transaction stack, not base database. 
    //   Need to be able to swap/drop trnastion layer.
    // Other transaction policies.
    //    MRSW-at-end, MRSW-for-transation, Mutex, 
    // Test - semiCommit, flush just nodes, etc etc
    // BPT caching root node.

    // Internal consistency checks.
    // Sort out replay journal.
    //  Do all partials, replay whole journal.
    
    // Reorg:
    //   Logged B+Trees (i.e. combine BlkMgr and B+Tree) 
    
    // Tasks:
    // * Check journal truncates to last commit.
    //   Journal needs reset markers
    // * Turn on/off all operations - commit => off.
    //   Not a wrapper - build into NodeTupleTable and 
    //   ==> DatasetControl 
    //   Note that query goes via NodeTupleTables.
    //   Should GraphNamedTDB use a NodeTupleTable view? 
    //   TripleTable as view of QuadTable. 
    
    // * CRC and bullet-proof read of Journal.
    // * Params.
    // * Assembler
    // * Dataset API / autocommit
    // * UUID per committed version to support etags
    // * Promote => duplicate even when not necessary.  BlockMgr property.
    // * Monitoring and stats : JMX.
    
    // Tidy up:
    // A DatasetGraphTDB is 3 NodeTupleTables.  Build as such.
    //    Triple/Quad/Prefix table to take a NodeTupleTable.

    // DatasetControl
    //   Change able and do ReadOnly this way.
    //   .setReadMode = affect (shared ) DatasetControl
    
    // ?? Journal for BlockMgrs only.
    //  System journal is just commits/aborts.
    
    // Iterator tracking.
    // NodeTupleTable.find [NodeTupleTableConcrete]
    //     Iterator<Tuple<NodeId>> find(Tuple<NodeId> tuple) ==> checkIterator: 
    //     **** Catch in NodeTupleTable.find
    
    // autocommit mode.
    //   Better to also wrap reading from the parser?
    //   WriteLock => start xAction.
    
    // TranasactionManager
    //   When looking for delayed replays, we could note the generation the
    //   activeReaders/activeWriters were working with and partially reduce
    //   the waiting queue (up to one outstanding commit?)
    
    // DSG.add(Quad(tripleInQuad, triple)) does not affect default graph.
    
    // * Check syncs NodeTupleTable, NodeTable keep a dirty flag 
    // * B+Tree and caching root block.
    //   BPT created per transaction so safe (?).
   
    // Design:
    //   Writing journal during a transaction, not just in prepare (scale)
    //   Write blobs via journal.
    
    // Tidy up 
    //   See HACK (BPTreeNode)
    //   See [TxTDB:PATCH-UP]
    //   See [TxTDB:TODO]
    //   See FREE
    //   See [ITER]
    
    // Optimizations:
    //   ByteBuffer.allocateDirect + pooling
    //     http://mail-archives.apache.org/mod_mbox/mina-dev/200804.mbox/%3C47F90DF0.6050101@gmail.com%3E
    //     http://mail-archives.apache.org/mod_mbox/mina-dev/200804.mbox/%3Cloom.20080407T064019-708@post.gmane.org%3E

    // Other:
    //   Sort out IndexBulder/IndexFactory/(IndexMaker in test)
    // TDB 0.8.10 is rev 8718; TxTDB forked at 8731
    // Diff of SF ref 8718 to Apache cross over applied. (src/ only)
    // Now Apache: rev 1124661 
}
