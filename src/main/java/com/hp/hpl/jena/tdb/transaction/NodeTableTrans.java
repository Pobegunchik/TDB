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

package com.hp.hpl.jena.tdb.transaction;

import java.nio.ByteBuffer ;
import java.util.Iterator ;

import org.openjena.atlas.iterator.Iter ;
import org.openjena.atlas.lib.ByteBufferLib ;
import org.openjena.atlas.lib.Pair ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.tdb.TDBException ;
import com.hp.hpl.jena.tdb.base.objectfile.ObjectFile ;
import com.hp.hpl.jena.tdb.base.record.Record ;
import com.hp.hpl.jena.tdb.index.Index ;
import com.hp.hpl.jena.tdb.nodetable.NodeTable ;
import com.hp.hpl.jena.tdb.nodetable.NodeTableCache ;
import com.hp.hpl.jena.tdb.nodetable.NodeTableInline ;
import com.hp.hpl.jena.tdb.nodetable.NodeTableNative ;
import com.hp.hpl.jena.tdb.store.NodeId ;

public class NodeTableTrans implements NodeTable, TransactionLifecycle
{
    private static Logger log = LoggerFactory.getLogger(NodeTableTrans.class) ;
    // TODO flag to note is any work is needed on commit.
    private final NodeTable base ;
    private long offset ;
    
    private NodeTable nodeTableJournal = null ;
    private static int CacheSize = 10000 ;      // [TxTDB:TODO] Make configurable 
    private boolean passthrough = false ;
    
    private Index nodeIndex ;
    private ObjectFile journalObjFile ;
    // Start of the journal file for this transaction.
    // Always zero currently but allows for future  
    private long journalObjFileStartOffset ; 
    private final String label ;
    private final Transaction txn ;     // Can be null (during recovery).
    
    // *** TEMPORARY!
    public static boolean FIXUP = false ;  
    
    public NodeTableTrans(Transaction txn, String label, NodeTable sub, Index nodeIndex, ObjectFile objFile)
    {
        this.txn = txn ;
        this.base = sub ;
        this.nodeIndex = nodeIndex ;
        this.journalObjFile = objFile ;
        this.label = label ; 
        // Show the way tables are wired up
        //debug("NTT[%s #%s] %s", label, Integer.toHexString(hashCode()), sub) ;
    }

    public void setPassthrough(boolean v)   { passthrough = v ; }
    public NodeTable getBaseNodeTable()     { return base ; }
    public NodeTable getJournalTable()      { return nodeTableJournal ; }
    public Transaction getTransaction()     { return txn ; }
    
    @Override
    public NodeId getAllocateNodeId(Node node)
    {
        if ( passthrough ) return base.getAllocateNodeId(node) ;
        NodeId nodeId = getNodeIdForNode(node) ;
        if ( ! NodeId.doesNotExist(nodeId) )
            return nodeId ;
        // add to journal
        nodeId = allocate(node) ;
        return nodeId ;
    }
    
    @Override
    public NodeId getNodeIdForNode(Node node)
    {
        if ( passthrough ) return base.getNodeIdForNode(node) ;
        NodeId nodeId = nodeTableJournal.getNodeIdForNode(node) ;
        if ( ! NodeId.doesNotExist(nodeId) )
            return mapFromJournal(nodeId) ;
        nodeId = base.getNodeIdForNode(node) ;
        return nodeId ;
    }

    /** Convert from a id to the id in the "journal" file */ 
    private NodeId mapToJournal(NodeId id)
    { 
        if ( passthrough )
           throw new TDBTransactionException("Not in an active transaction") ;
        if ( NodeId.isInline(id) )
            return id ; 
        return NodeId.create(id.getId()-offset) ;
    }
    
    /** Convert from a id in other to an external id  */ 
    private NodeId mapFromJournal(NodeId id)
    { 
        if ( passthrough )
            throw new TDBTransactionException("Not in an active transaction") ;
        if ( NodeId.isInline(id) )
            return id ; 
        return NodeId.create(id.getId()+offset) ; 
    }
    
    @Override
    public Node getNodeForNodeId(NodeId id)
    {
        if ( passthrough ) return base.getNodeForNodeId(id) ;
        long x = id.getId() ;
        if ( x < offset )
            return base.getNodeForNodeId(id) ;
        id = mapToJournal(id) ;
        Node node = nodeTableJournal.getNodeForNodeId(id) ;
        return node ;
    }

    private NodeId allocate(Node node)
    {
        NodeId nodeId = nodeTableJournal.getAllocateNodeId(node) ;
        nodeId = mapFromJournal(nodeId) ;
        return nodeId ;
    }
    
    @Override
    public NodeId allocOffset()
    {
        if ( passthrough ) return base.allocOffset() ;
        // If we have done the append stage, this is invalid as the base may change under our feet
        // Would need to track base operations.
        NodeId x1 = nodeTableJournal.allocOffset() ;
        NodeId x2 = mapFromJournal(x1) ;
        return x2 ;
    }

    @Override
    public void begin(Transaction txn)
    {
        if ( this.txn.getTxnId() != txn.getTxnId() )
            throw new TDBException(String.format("Different transactions: %s %s", this.txn.getLabel(), txn.getLabel())) ;
        if ( passthrough )
            throw new TDBException("Already active") ;
        passthrough = false ;
        
        offset = base.allocOffset().getId() ;

        journalObjFileStartOffset = journalObjFile.length() ;
        if ( journalObjFileStartOffset != 0 )
        {
            System.out.flush() ;
            System.err.printf("\n%s journalStartOffset not zero: %d/0x%02X\n",txn.getLabel(), journalObjFileStartOffset, journalObjFileStartOffset) ;
            
            // repeat for debugging.
            journalObjFile.length() ;
            
            if ( FIXUP )
            {
                // TEMP : if you see this code active in SVN, set it to false immediately.
                // The question is how come the journal position was non-zero in the first place. 
                System.err.println("journalStartOffset reset to zero") ;
                journalObjFileStartOffset = 0 ;
                journalObjFile.truncate(0) ;
                journalObjFile.sync() ;
            }
        }
        offset += journalObjFileStartOffset ;
        
        //debug("begin: %s %s", txn.getLabel(), label) ;
        //debug("begin: base=%s  offset=0x%X journalOffset=0x%X", base, offset, journalOffset) ;
        
        this.nodeTableJournal = new NodeTableNative(nodeIndex, journalObjFile) ;
        this.nodeTableJournal = NodeTableCache.create(nodeTableJournal, CacheSize, CacheSize) ;
        // map to/from Journal knows about unmappable inline values. 
        this.nodeTableJournal = NodeTableInline.create(nodeTableJournal) ;
        
    }
    
    /** Copy from the journal file to the real file */
    /*package*/ void append()
    {
        //debug(">> append: %s",label) ;
        
        // Assumes all() is in order from low to high.
        Iterator<Pair<NodeId, Node>> iter = nodeTableJournal.all() ;
        for ( ; iter.hasNext() ; )
        {
            Pair<NodeId, Node> x = iter.next() ;
            NodeId nodeId = x.getLeft() ;
            Node node = x.getRight() ;
            //debug("append: %s -> %s", x, mapFromJournal(nodeId)) ;
            // This does the write.
            NodeId nodeId2 = base.getAllocateNodeId(node) ;
            if ( ! nodeId2.equals(mapFromJournal(nodeId)) )
            {
                String msg = String.format("Different ids for %s: allocated: expected %s, got %s", node, mapFromJournal(nodeId), nodeId2) ;
                System.err.println() ;
                System.err.println() ;
                System.err.println(msg) ;
                dump() ;   
                System.err.println() ;
                throw new TDBException(msg) ;
            }
        }
        //debug("<< append: %s",label) ;
    }
    
    private void dump()
    {
        System.err.println(">>>>>>>>>>") ;
        System.err.println("label = "+label) ;
        System.err.println("txn = "+txn) ;
        System.err.println("offset = "+offset) ;
        System.err.println("journalStartOffset = "+journalObjFileStartOffset) ;
        System.err.println("journal = "+journalObjFile.getLabel()) ;
        if ( true )
            return ;
        
        System.err.println("nodeTableJournal >>>") ;
        Iterator<Pair<NodeId, Node>> iter = nodeTableJournal.all() ;
        for ( ; iter.hasNext() ; )
        {
            Pair<NodeId, Node> x = iter.next() ;
            NodeId nodeId = x.getLeft() ;
            Node node = x.getRight() ;
            NodeId mapped = mapFromJournal(nodeId) ;
            //debug("append: %s -> %s", x, mapFromJournal(nodeId)) ;
            // This does the write.
            NodeId nodeId2 = base.getAllocateNodeId(node) ;
            System.err.println(x + "  mapped=" + mapped + " getAlloc="+nodeId2) ;
        }
        
        System.err.println("journal >>>") ;
        Iterator<Pair<Long, ByteBuffer>> iter1 = this.journalObjFile.all() ;
        for ( ; iter1.hasNext() ; )
        {
            Pair<Long, ByteBuffer> p = iter1.next() ;
            System.err.println(p.getLeft()+" : "+p.getRight()) ;
            ByteBufferLib.print(System.err, p.getRight()) ;
        }
        
        System.err.println("nodeIndex >>>") ;
        Iterator<Record> iter2 = this.nodeIndex.iterator() ;
        for ( ; iter2.hasNext() ; )
        {
            Record r = iter2.next() ;
            System.err.println(r) ;
        }
        System.err.println("<<<<<<<<<<") ;
    }
    
    @Override
    public void commitPrepare(Transaction txn)
    {
        debug("%s >> commitPrepare: %s", txn.getLabel(), label) ;
        // The node table is append-only so it can be written during prepare.
        // The index isn't written (via the transaction journal) until enact.
        if ( nodeTableJournal == null )
            throw new TDBTransactionException(txn.getLabel()+": Not in a transaction for a commit to happen") ;
        writeNodeJournal() ;
        
        if ( journalObjFile != null && journalObjFile.length() != 0 )
        {
            long x = journalObjFile.length() ;
            throw new TDBTransactionException(txn.getLabel()+": journalObjFile not cleared ("+x+")") ;
        }
        debug("%s << commitPrepare: %s", txn.getLabel(), label) ;
    }
    
    @Override
    public void commitEnact(Transaction txn)
    {
        // The work was done in commitPrepare, using the fact that node data file
        // is append only.  Until here, pointers to the extra data aren't available
        // until the index is written.
        // The index is written via the transaction journal.
        
        //debug("commitEnact: %s", label) ;
        //writeJournal() ;
    }

    private void writeNodeJournal()
    {
        if ( nodeTableJournal.isEmpty() )
            return ;
        
        //debug("writeNodeJournal: (base alloc before) %s", base.allocOffset()) ;
        append() ;
        //debug("writeNodeJournal: (base alloc after) %s",  base.allocOffset()) ;
        //debug("writeNodeJournal: (nodeTableJournal) %s", nodeTableJournal.allocOffset()) ;
        
        // Reset (in case we use this again)
        nodeIndex.clear() ;
        // Fixes nodeTableJournal
        journalObjFile.truncate(journalObjFileStartOffset) ;
        //journalObjFile.sync() ;
        journalObjFile.close() ;
        journalObjFile = null ;
        base.sync() ;
        offset = -99 ; // base.allocOffset().getId() ; // Will be invalid as we may write through to the base table later.
        passthrough = true ;
    }

    @Override
    public void commitClearup(Transaction txn)
    {
        debug("%s ** commitClearup: %s",  txn.getLabel(), label) ;
        finish() ;
    }

    @Override
    public void abort(Transaction txn)
    {
        if ( nodeTableJournal == null )
            throw new TDBTransactionException(txn.getLabel()+": Not in a transaction for a commit to happen") ;
        // Ensure the cache does not flush.
        nodeTableJournal = null ;
        // then make sure the journal file is empty.
        journalObjFile.truncate(journalObjFileStartOffset) ;
        journalObjFile.sync() ;
        finish() ;
    }
    
    private void finish()
    {
        close() ;
        passthrough = true ;
        nodeTableJournal = null ;
        journalObjFile = null ;
   }

    @Override
    public Iterator<Pair<NodeId, Node>> all()
    {
        // Better would be to convert the spill file format.
        return Iter.concat(base.all(), nodeTableJournal.all()) ;
    }

    @Override
    public boolean isEmpty()
    {
        return nodeTableJournal.isEmpty() && base.isEmpty() ;
    }

    @Override
    public void sync()
    {}

    @Override
    public void close()
    {
        if ( nodeIndex != null )
            nodeIndex.close() ;
        nodeIndex = null ;
        // Closing the journal flushes it; i.e. disk IO. 
        if ( journalObjFile != null )
            journalObjFile.close() ;
        journalObjFile = null ;
    }

    @Override
    public String toString() { return "NodeTableTrans:"+label+"(#"+Integer.toHexString(super.hashCode())+")" ; }
    
    private void debug(String fmt, Object... args)
    {
        if ( log.isDebugEnabled() )
        {
            String x = String.format(fmt, args) ;
            log.debug(x) ;
        }
    }
}
