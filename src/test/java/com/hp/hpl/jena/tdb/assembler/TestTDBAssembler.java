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

package com.hp.hpl.jena.tdb.assembler;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openjena.atlas.junit.BaseTest ;
import org.openjena.atlas.lib.FileOps ;

import com.hp.hpl.jena.assembler.JA;
import com.hp.hpl.jena.assembler.exceptions.AssemblerException ;
import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.sparql.core.assembler.AssemblerUtils;
import com.hp.hpl.jena.sparql.core.assembler.DatasetAssemblerVocab;
import com.hp.hpl.jena.tdb.ConfigTest;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB;
import com.hp.hpl.jena.tdb.store.GraphNamedTDB;
import com.hp.hpl.jena.tdb.store.GraphTDB;
import com.hp.hpl.jena.tdb.store.GraphTDBBase;
import com.hp.hpl.jena.tdb.store.GraphTriplesTDB;

public class TestTDBAssembler extends BaseTest
{
    // Can be slow - explicitly closes the dataset.
    static final String dirAssem    = "testing/Assembler" ;
    static final String dirDB       = ConfigTest.getTestingDir()+"/DB" ;

    @BeforeClass static public void beforeClass()
    {
        FileOps.ensureDir(dirDB) ;
    }
    
    @Before public void before()
    {
        FileOps.clearDirectory(dirDB) ;
    }
    
    @AfterClass static public void afterClass()
    {
        FileOps.clearDirectory(dirDB) ;
    }
    
    @Test public void createDatasetDirect()
    {
        String f = dirAssem+"/tdb-dataset.ttl" ;
        Object thing = AssemblerUtils.build( f, VocabTDB.tDatasetTDB) ;
        assertTrue(thing instanceof Dataset) ;
        Dataset ds = (Dataset)thing ;
        ds.asDatasetGraph() ;
        assertTrue(((Dataset)thing).asDatasetGraph() instanceof DatasetGraphTDB) ;
        ds.close() ;
    }
    
    @Test public void createDatasetEmbed()
    {
        String f = dirAssem+"/tdb-dataset-embed.ttl" ;
        Object thing = AssemblerUtils.build( f, DatasetAssemblerVocab.tDataset) ;
        assertTrue(thing instanceof Dataset) ;
        Dataset ds = (Dataset)thing ;
        assertTrue(ds.asDatasetGraph() instanceof DatasetGraphTDB) ;
        ds.close();
    }

    @Test public void createGraphDirect()
    {
        testGraph(dirAssem+"/tdb-graph.ttl", false) ;
    }
    
    @Test public void createGraphEmbed()
    {
        String f = dirAssem+"/tdb-graph-embed.ttl" ;
        Object thing = null ;
        try { thing = AssemblerUtils.build( f, JA.Model) ; }
        catch (AssemblerException e)
        { 
            e.getCause().printStackTrace(System.err) ;
            throw e ;
        }
        
        assertTrue(thing instanceof Model) ;
        Graph graph = ((Model)thing).getGraph() ;
        
        assertTrue(graph instanceof GraphTDB) ; 
        assertTrue(graph instanceof GraphTriplesTDB) ;
        assertFalse(graph instanceof GraphNamedTDB) ;

        DatasetGraphTDB ds = ((GraphTDBBase)graph).getDataset() ;
        if ( ds != null )
            ds.close();
    }
    
    @Test public void createNamedGraph1()
    {
        testGraph(dirAssem+"/tdb-named-graph-1.ttl", true) ;
    }
    
    @Test public void createNamedGraph2()
    {
        testGraph(dirAssem+"/tdb-named-graph-2.ttl", true) ;
    }
    
    @Test public void createNamedGraphViaDataset()
    {
        testGraph(dirAssem+"/tdb-graph-ref-dataset.ttl",false) ;
    }

    private static void testGraph(String assemblerFile, boolean named) 
    {
        Object thing = null ;
        try { thing = AssemblerUtils.build( assemblerFile, VocabTDB.tGraphTDB) ; }
        catch (AssemblerException e)
        { 
            e.getCause().printStackTrace(System.err) ;
            throw e ;
        }


        assertTrue(thing instanceof Model) ;
        Graph graph = ((Model)thing).getGraph() ;
        
        assertTrue(graph instanceof GraphTDB) ; 
        if ( named )
        {
            assertFalse( graph instanceof GraphTriplesTDB) ;
            assertTrue(graph instanceof GraphNamedTDB) ;
        }
        else
        {
            assertTrue( graph instanceof GraphTriplesTDB) ;
            assertFalse(graph instanceof GraphNamedTDB) ;
        }
        
        DatasetGraphTDB ds = ((GraphTDBBase)graph).getDataset() ;
        if ( ds != null )
            ds.close();
    }
}
