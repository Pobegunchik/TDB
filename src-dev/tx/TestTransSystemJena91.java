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

package tx ;

import static com.hp.hpl.jena.tdb.transaction.TransTestLib.count ;
import static java.lang.String.format ;

import java.util.ArrayList ;
import java.util.Date ;
import java.util.List ;
import java.util.Random ;
import java.util.concurrent.Callable ;
import java.util.concurrent.ExecutorService ;
import java.util.concurrent.Executors ;
import java.util.concurrent.TimeUnit ;
import java.util.concurrent.atomic.AtomicInteger ;

import org.junit.AfterClass ;
import org.junit.BeforeClass ;
import org.openjena.atlas.lib.FileOps ;
import org.openjena.atlas.lib.Lib ;
import org.openjena.atlas.lib.RandomLib ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype ;
import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.query.Dataset ;
import com.hp.hpl.jena.rdf.model.Model ;
import com.hp.hpl.jena.rdf.model.Property ;
import com.hp.hpl.jena.rdf.model.RDFNode ;
import com.hp.hpl.jena.rdf.model.Resource ;
import com.hp.hpl.jena.rdf.model.Statement ;
import com.hp.hpl.jena.rdf.model.StmtIterator ;
import com.hp.hpl.jena.sparql.core.Quad ;
import com.hp.hpl.jena.sparql.sse.SSE ;
import com.hp.hpl.jena.tdb.ConfigTest ;
import com.hp.hpl.jena.tdb.DatasetGraphTxn ;
import com.hp.hpl.jena.tdb.ReadWrite ;
import com.hp.hpl.jena.tdb.StoreConnection ;
import com.hp.hpl.jena.tdb.base.block.FileMode ;
import com.hp.hpl.jena.tdb.base.file.Location ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;
import com.hp.hpl.jena.tdb.transaction.SysTxnState ;
import com.hp.hpl.jena.tdb.transaction.TransactionManager ;

/** System testing of the transactions.
 * Version from Simon Helsen shows issues on windows
 * JENA-91
 * JENA-115 
 */
public class TestTransSystemJena91
{
    static { org.openjena.atlas.logging.Log.setLog4j() ; }
    private static Logger log = LoggerFactory.getLogger(TestTransSystemJena91.class) ;

    /* Notes:
	 * MS Windows does not allow memory mapped files to be deleted during the run of a JVM.
	 * This means we can't delete a database and reuse it's directory (see clean()).
	 * Therefore, this test program this does not run on MS Windows 64 bit mode.
	 */
	
    static { 
    	//SystemTDB.isWindows
        if ( true )
            SystemTDB.setFileMode(FileMode.direct) ;
        
        if ( SystemTDB.isWindows && SystemTDB.fileMode() == FileMode.mapped )
        	log.error("**** Running with file mapped mode on MS Windows - expected test failure") ;
    }

    static boolean MEM = false ;
    
    static final Location LOC = MEM ? Location.mem() : new Location(ConfigTest.getTestingDirDB()) ;

    static final int Iterations             = MEM ? 1000 : 1000 ;
    // Output style.
    static boolean inlineProgress           = true ; // (! log.isDebugEnabled()) && Iterations > 20 ;
    static boolean logging                  = ! inlineProgress ; // (! log.isDebugEnabled()) && Iterations > 20 ;
    
    /*
     * 5/0/5 blocks. with 50/50 pause, 50R/ 20W
     * Others?
     */

    static final int numReaderTasks         = 5 ;
    static final int numWriterTasksA        = 2 ; 
    static final int numWriterTasksC        = 5 ;

    static final int readerSeqRepeats       = 8 ;
    static final int readerMaxPause         = 25 ;

    static final int writerAbortSeqRepeats  = 4 ;
    static final int writerCommitSeqRepeats = 4 ;
    static final int writerMaxPause         = 20 ;

    static String[] properties = { 
        "java.version", 
        "java.vendor",
        "java.runtime.name",
        "java.runtime.version" ,
        "java.vm.version" ,
        "java.vm.vendor" ,
        "java.vm.name" ,
        "java.vm.specification.name" ,
        "java.vm.info", 
        "java.class.version", 
        "java.specification.version" ,
        "java.specification.vendor", 
        "os.name", 
        "os.version", 
        "java.class.path", 
        "java.home" 
    } ;
    
    public static void main(String...args)
    {
        System.out.println(TestTransSystemJena91.class.getSimpleName()) ;
        System.out.println() ;
        System.out.println(new Date().toString()) ;
        for ( String k : properties )
        {
            String v = System.getProperty(k) ;
            System.out.printf("%-30s = %s\n", k, v) ;
        }
        System.out.println() ;

        String x = (MEM?"memory":"disk["+SystemTDB.fileMode()+"]") ;
        
        if ( logging )
            log.info("START ({}, {} iterations)", x, Iterations) ;
        else
            printf("START (%s, %d iterations)\n", x, Iterations) ;
        
        int N = (Iterations < 10) ? 1 : Iterations / 10 ;
        N = Math.min(N, 100) ;
        int i ;
        
        for ( i = 0 ; i < Iterations ; i++ )
        {
            clean() ;
            
            if (!inlineProgress && logging)
                log.info(format("Iteration: %d\n", i)) ;
            if ( inlineProgress )
            {
                if ( i%N == 0 )
                    printf("%03d: ",i) ;
                printf(".") ;
                if ( i%N == (N-1) )
                    println() ;
            }
            new TestTransSystemJena91().manyReaderAndOneWriter() ;
        }
        if ( inlineProgress )
        {
            if ( i%N != 0 )
                System.out.println() ;
            println() ;
            printf("DONE (%03d)\n",i) ;
        }
        if (logging)
            log.info("FINISH ({})", i) ;
        else
            printf("FINISH") ;
    }
    
    private static void clean()
    {
    	
        StoreConnection.release(LOC) ;
        if ( ! LOC.isMem() )
            FileOps.clearDirectory(LOC.getDirectoryPath()) ;
    }

    static class Reader implements Callable<Object>
    {
        private final int repeats ;
        private final int maxpause ;
        private final StoreConnection sConn ; 
    
        Reader(StoreConnection sConn, int numSeqRepeats, int pause)
        {
            this.repeats = numSeqRepeats ;
            this.maxpause = pause ;
            this.sConn = sConn ;
        }
    
        @Override
        public Object call()
        {
            DatasetGraphTxn dsg = null ;
            try
            {
                int id = gen.incrementAndGet() ;
                for (int i = 0; i < repeats; i++)
                {
                    dsg = sConn.begin(ReadWrite.READ) ;
                    log.debug("reader start " + id + "/" + i) ;

                    // TEST MODEL READ
                    readModel(dsg.toDataset());
                    
                    int x1 = count("SELECT * { ?s ?p ?o }", dsg) ;
                    pause(maxpause) ;
                    int x2 = count("SELECT * { ?s ?p ?o }", dsg) ;
                    if (x1 != x2) log.warn(format("READER: %s Change seen: %d/%d : id=%d: i=%d",
                                                  dsg.getTransaction().getLabel(), x1, x2, id, i)) ;
                    log.debug("reader finish " + id + "/" + i) ;
                    dsg.close() ;
                    dsg = null ;
                }
                return null ;
            } catch (RuntimeException ex)
            {
                ex.printStackTrace(System.err) ;
                if ( dsg != null )
                {
                    dsg.abort() ;
                    dsg.close() ;
                    dsg = null ;
                }
                return null ;
            }
        }
    }

    static abstract class Writer implements Callable<Object>
    {
        private final int repeats ;
        private final int maxpause ;
        private final StoreConnection sConn ;
        private final boolean commit ; 
    
        protected Writer(StoreConnection sConn, int numSeqRepeats, int pause, boolean commit)
        {
            this.repeats = numSeqRepeats ;
            this.maxpause = pause ;
            this.sConn = sConn ;
            this.commit = commit ;
        }
        
        @Override
        public Object call()
        {
            DatasetGraphTxn dsg = null ;
            try { 
                int id = gen.incrementAndGet() ;
                for ( int i = 0 ; i < repeats ; i++ )
                {
                    dsg = sConn.begin(ReadWrite.WRITE) ;
                    log.debug("writer start "+id+"/"+i) ;

                    int x1 = count("SELECT * { ?s ?p ?o }", dsg) ;
                    int z = change(dsg, id, i) ;
                    pause(maxpause) ;
                    int x2 = count("SELECT * { ?s ?p ?o }", dsg) ;
                    
                    // TEST MODEL WRITE
                    writeModel(dsg.toDataset(), new Random().nextLong());
                   
                    if ( x1+z != x2 )
                    {
                        TransactionManager txnMgr = dsg.getTransaction().getTxnMgr() ;
                        SysTxnState state = txnMgr.state() ;
                        String label = dsg.getTransaction().getLabel() ; 
                        log.warn(format("WRITER: %s Change seen: %d + %d != %d : id=%d: i=%d", label, x1, z, x2, id, i)) ;
                        log.warn(state.toString()) ;
                        dsg.abort() ;
                        dsg.close() ;
                        dsg = null ;
                        return null ;
                    }
                    if (commit) 
                        dsg.commit() ;
                    else
                        dsg.abort() ;
                    SysTxnState state = sConn.getTransMgrState() ;
                    log.debug(state.toString()) ;
                    log.debug("writer finish "+id+"/"+i) ;                
                    dsg.close() ;
                    dsg = null ;
                }
                return null ;
            }
            catch (RuntimeException ex)
            { 
                ex.printStackTrace(System.err) ;
                System.exit(1) ;
                if ( dsg != null )
                {
                    dsg.abort() ;
                    dsg.close() ;
                    dsg = null ;
                }
                return null ;
            }
        }
    
        // return the delta.
        protected abstract int change(DatasetGraphTxn dsg, int id, int i) ;
    }

    @BeforeClass 
    public static void beforeClass()
    {
        if ( ! LOC.isMem() )
            FileOps.clearDirectory(LOC.getDirectoryPath()) ;
        StoreConnection.reset() ;
        StoreConnection sConn = StoreConnection.make(LOC) ;
        DatasetGraphTxn dsg = sConn.begin(ReadWrite.WRITE) ;
        dsg.add(q1) ;
        dsg.add(q2) ;
        initCount = 2 ;
        dsg.commit() ;
        dsg.close() ;
    }
    
    @AfterClass 
    public static void afterClass() {}

    private StoreConnection sConn ;
    protected synchronized StoreConnection getStoreConnection()
    {
        StoreConnection sConn = StoreConnection.make(LOC) ;
        //sConn.getTransMgr().recording(true) ;
        return sConn ;
    }
    
    public TestTransSystemJena91() {}
        
    //@Test
    public void manyRead()
    {
        final StoreConnection sConn = getStoreConnection() ;
        Callable<?> proc = new Reader(sConn, 50, 200)  ;        // Number of repeats, max pause
            
        for ( int i = 0 ; i < 5 ; i++ )
            execService.submit(proc) ;
        try
        {
            execService.shutdown() ;
            execService.awaitTermination(100, TimeUnit.SECONDS) ;
        } catch (InterruptedException e)
        {
            e.printStackTrace(System.err) ;
        }
    }
    
    //@Test
    public void manyReaderAndOneWriter()
    {
        final StoreConnection sConn = getStoreConnection() ;
        
        Callable<?> procR = new Reader(sConn, readerSeqRepeats, readerMaxPause) ;      // Number of repeats, max pause
        Callable<?> procW_a = new Writer(sConn, writerAbortSeqRepeats, writerMaxPause, false)  // Number of repeats, max pause, commit. 
        {
            @Override
            protected int change(DatasetGraphTxn dsg, int id, int i)
            {  
                return changeProc(dsg, id, i) ; 
            }
        } ;
            
        Callable<?> procW_c = new Writer(sConn, writerCommitSeqRepeats, writerMaxPause, true)  // Number of repeats, max pause, commit. 
        {
            @Override
            protected int change(DatasetGraphTxn dsg, int id, int i)
            { 
                return changeProc(dsg, id, i) ;
            }
        } ;

        submit(execService, procR,   numReaderTasks) ;
        submit(execService, procW_c, numWriterTasksC) ;
        submit(execService, procW_a, numWriterTasksA) ;
        
        try
        {
            execService.shutdown() ;
            execService.awaitTermination(100, TimeUnit.SECONDS) ;
        } catch (InterruptedException e)
        {
            e.printStackTrace(System.err) ;
        } 
    }

    private void submit(ExecutorService execService2, Callable<?> proc, int numTasks)
    {
        for ( int i = 0 ; i < numTasks ; i++ )
            execService.submit(proc) ;
    }

    static int changeProc(DatasetGraphTxn dsg, int id, int i)
    {
        int count = 0 ;
        int maxN = 500 ;
        int N = RandomLib.qrandom.nextInt(maxN) ;
        for ( int j = 0 ; j < N; j++ )
        {
            Quad q = genQuad(id*maxN+j) ;
            if ( ! dsg.contains(q) )
            {
                dsg.add(q) ;
                count++ ;
            }
        }
        log.debug("Change = "+dsg.getDefaultGraph().size()) ;
        return count ;
    }
    
    static void pause(int maxInternal)
    {
        int x = (int)Math.round(Math.random()*maxInternal) ;
        Lib.sleep(x) ;
    }
    
    static Quad genQuad(int value)
    {
        Quad q1 = SSE.parseQuad("(_ <s> <p> <o>)") ;
        Node g1 = q.getGraph() ;
        
        Node g = Quad.defaultGraphNodeGenerated ; // urn:x-arq:DefaultGraphNode
        Node s = Node.createURI("S") ;
        Node p = Node.createURI("P") ;
        Node o = Node.createLiteral(Integer.toString(value), null, XSDDatatype.XSDinteger) ;
        return new Quad(g,s,p,o) ;
    }

    private static void println()
    {
        printf("\n") ; System.out.flush() ;
    }

    private static void printf(String string, Object...args)
    {
        System.out.printf(string, args) ;
    }

    private ExecutorService execService = Executors.newCachedThreadPool() ;

    static Quad q  = SSE.parseQuad("(_ <s> <p> <o>) ") ;

    static Quad q1 = SSE.parseQuad("(_ <s> <p> <o1>)") ;

    static Quad q2 = SSE.parseQuad("(_ <s> <p> <o2>)") ;

    static Quad q3 = SSE.parseQuad("(_ <s> <p> <o3>)") ;

    static Quad q4 = SSE.parseQuad("(_ <s> <p> <o4>)") ;

    private static int initCount = -1 ;

    //static final Location LOC = new Location(ConfigTest.getTestingDirDB()) ;
    static final AtomicInteger gen = new AtomicInteger() ;
      
    private final static String INDEX_INFO_SUBJECT = "http://jazz.net/xmlns/foundation/1.0/myTestSubject";
    private final static String[] lastProcessedURIs = new String[]{"https://test/t1", "https://test/t2"};
    private final static String TIMESTAMP_PREDICATE = "http://jazz.net/xmlns/foundation/1.0/myTestTimestampPredicate";
    private final static String URI_PREDICATE = "http://jazz.net/xmlns/foundation/1.0/myTestUriPredicate";
    private final static String VERSION_PREDICATE = "http://jazz.net/xmlns/foundation/1.0/myTestVersionPredicate";
    
    private static void writeModel(Dataset ds, long ts) {
    	Model timestampModel = ds.getNamedModel(INDEX_INFO_SUBJECT);
		timestampModel.removeAll();
		Resource subject = timestampModel.createResource(INDEX_INFO_SUBJECT);
		Property predicate = timestampModel.createProperty(TIMESTAMP_PREDICATE);
		timestampModel.addLiteral(subject, predicate, ts);
		predicate = timestampModel.createProperty(URI_PREDICATE);
		for (String uri : lastProcessedURIs) {
			timestampModel.add(subject, predicate, timestampModel.createResource(uri));
		}
		predicate = timestampModel.createProperty(VERSION_PREDICATE);
		timestampModel.addLiteral(subject, predicate, 1.0);
    }
    
    private static void readModel(Dataset ds) {
		Model timestampModel = ds.getNamedModel(INDEX_INFO_SUBJECT);
		StmtIterator statements = timestampModel.listStatements();
		try {
			long timestamp = -1;
			float indexVersion = 0;
			List<String> uris = new ArrayList<String>(2);
			while (statements.hasNext()) {
				Statement statement = statements.next();
				if (INDEX_INFO_SUBJECT.equals(statement.getSubject().getURI())) {
					String predicate = statement.getPredicate().getURI();
					if (TIMESTAMP_PREDICATE.equals(predicate)) {
						timestamp = statement.getLong();
					} else if (VERSION_PREDICATE.equals(predicate)) {
						indexVersion = statement.getFloat();
					} else if (URI_PREDICATE.equals(predicate)) {
						RDFNode object = statement.getObject();
						if (object.isURIResource()) {
							uris.add(((Resource) object).getURI());
						}
					}
				}
			}
		} finally {
			statements.close();
		}
    }
}
