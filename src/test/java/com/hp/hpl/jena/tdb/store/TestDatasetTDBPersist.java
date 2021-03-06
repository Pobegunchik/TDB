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

package com.hp.hpl.jena.tdb.store;


import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openjena.atlas.iterator.Iter;
import org.openjena.atlas.junit.BaseTest;
import org.openjena.atlas.lib.FileOps;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.sparql.sse.SSE;
import com.hp.hpl.jena.sparql.util.NodeFactory;
import com.hp.hpl.jena.tdb.ConfigTest;
import com.hp.hpl.jena.tdb.base.file.Location;
import com.hp.hpl.jena.tdb.junit.GraphLocation;
import com.hp.hpl.jena.tdb.sys.SystemTDB;
import com.hp.hpl.jena.tdb.sys.TDBMaker;

/** Testing persistence  */ 
public class TestDatasetTDBPersist extends BaseTest
{
    static Node n0 = NodeFactory.parseNode("<http://example/n0>") ; 
    static Node n1 = NodeFactory.parseNode("<http://example/n1>") ;
    static Node n2 = NodeFactory.parseNode("<http://example/n2>") ;
    static boolean nonDeleteableMMapFiles = SystemTDB.isWindows ;
    
    // To avoid the problems on MS Windows whereby memory mapped files
    // can't be deleted from a running JVM, we use a different, cleaned 
    // directory each time.

    GraphLocation graphLocation = null ;
    
    @Before public void before()
    {   
    	String dirname = nonDeleteableMMapFiles ? ConfigTest.getTestingDirUnique() : ConfigTest.getTestingDir() ;
		FileOps.ensureDir(dirname) ;
		FileOps.clearDirectory(dirname) ;
		graphLocation = new GraphLocation(new Location(dirname)) ;
        graphLocation.createDataset() ;
    }
    
    @After public void after()
    {
    	TDBMaker.clearDatasetCache() ;
    	if ( graphLocation != null )
    		graphLocation.release() ;
    	graphLocation.clearDirectory() ;	// Does nto have the desired effect on Windows.
    }
    
    @Test public void dataset1()
    {
        Dataset ds = graphLocation.getDataset() ;
        assertTrue( ds.asDatasetGraph() instanceof DatasetGraphTDB ) ;
        assertTrue( ds.getDefaultModel().getGraph() instanceof GraphTriplesTDB ) ;
        assertTrue( ds.getNamedModel("http://example/").getGraph() instanceof GraphNamedTDB ) ;
    }
    
    @Test public void dataset2()
    {
        Dataset ds = graphLocation.getDataset() ;
        Graph g1 = ds.getDefaultModel().getGraph() ;
        Graph g2 = ds.getNamedModel("http://example/").getGraph() ;
        
        g1.add(new Triple(n0,n1,n2) ) ;
        assertTrue(g1.contains(n0,n1,n2) ) ;
        assertFalse(g2.contains(n0,n1,n2) ) ;
    }

    @Test public void dataset3()
    {
        Dataset ds = graphLocation.getDataset() ;
        Graph g1 = ds.getDefaultModel().getGraph() ;
        // Sometimes, under windows, deleting the files by 
        // graphLocation.clearDirectory does not work.  
        // Needed for safe tests on windows.
        g1.getBulkUpdateHandler().removeAll() ;
        
        Graph g2 = ds.getNamedModel("http://example/").getGraph() ;
        g2.add(new Triple(n0,n1,n2) ) ;
        assertTrue(g2.contains(n0,n1,n2) ) ;
        assertFalse(g1.contains(n0,n1,n2) ) ;
    }

    @Test public void dataset4()
    {
        String graphName = "http://example/" ;
        Triple triple = SSE.parseTriple("(<x> <y> <z>)") ;
        Node gn = Node.createURI(graphName) ;

        Dataset ds = graphLocation.getDataset() ;
        // ?? See TupleLib.
        ds.asDatasetGraph().deleteAny(gn, null, null, null) ;
        
        Graph g2 = ds.asDatasetGraph().getGraph(gn) ;
        
//        if ( true )
//        {
//            PrintStream ps = System.err ;
//            ps.println("Dataset names: ") ;
//            Iter.print(ps, ds.listNames()) ;
//        }
        
        // Graphs only exists if they have a triple in them
        assertFalse(ds.containsNamedModel(graphName)) ;
        
        Iterator<String> iter = ds.listNames() ;
        assertFalse(iter.hasNext()) ;
        
        assertEquals(0, ds.asDatasetGraph().size()) ;
    }
    
    @Test public void dataset5()
    {
        String graphName = "http://example/" ;
        Triple triple = SSE.parseTriple("(<x> <y> <z>)") ;
        Dataset ds = graphLocation.getDataset() ;
        Graph g2 = ds.asDatasetGraph().getGraph(Node.createURI(graphName)) ;
        // Graphs only exists if they have a triple in them
        g2.add(triple) ;
        
        assertTrue(ds.containsNamedModel(graphName)) ;
        Iterator<String> iter = ds.listNames() ;
        List<String> x = Iter.toList(iter) ;
        List<String> y = Arrays.asList(graphName) ;
        assertEquals(x,y) ;
        
        assertEquals(1, ds.asDatasetGraph().size()) ;
    }
}
