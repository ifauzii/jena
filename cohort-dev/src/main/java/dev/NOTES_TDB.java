/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  See the NOTICE file distributed with this work for additional
 *  information regarding copyright ownership.
 */

package dev;

import org.apache.jena.sparql.core.DatasetPrefixStorage ;
import org.seaborne.dboe.base.file.Location ;
import org.seaborne.tdb2.store.QuadTable ;
import org.seaborne.tdb2.store.TripleTable ;

public class NOTES_TDB {
    // Hash functions http://aras-p.info/blog/2016/08/09/More-Hash-Function-Tests/
    // Guava Hashing
    
    // Promote - roll back TDB1 improvements.
    // arq.query : needs Txn wrapper
    
    // DatasetGraphTDB.requireWriteTransaction() and in GraphTDB.
    // c.f. getW() ;
    
    // ReadWrite => TxnMode(WRITE, READ(=READ_ISOLATED), READ_COMMITED, READ_ONLY, READ_ISOLATED , ISOLATED)
    //  begin() == begin(READ_ISOLATED) ;
    
    // Counting begin-end for nested transactions.
    //   TransactionalBase??

    // Cachign graphs/model in the transaction object
    
    // proper non-transactional.
    //SystemTDB.setNonTransactional
    
    // ** Reaper.
    // ** Or time-based, commit keeping.  benefit!
    // Autocommit (if we must!)
    
    // ** Recorder.
    // cf. DatasetGraphWithRecord
    
    // Also per index reaping.
    // Long term: break into parts: separate processes? At least threads.
    
    // see package txnlog

    // Transactions and nodes.dat-jrnl
    
    // tdb2loader.
    
    // Exclusive mode uses a reentrant RW lock so the calling thread is unaffected.
    // Consider a pair of plain locks or a semaphore (?)
    // http://stackoverflow.com/questions/10711447/is-there-a-non-reentrant-readwritelock-i-can-use
    // http://codereview.stackexchange.com/questions/17913/java-non-reentrant-lock-implementation

    // Reaper::
    // Needs to work with the switching DatasetGraphTDB.
    //   Abstract DatasetGraphTDB as an interface!
    //     Storage unit to have indexes and node table.
    
    static class StorageTDB {
        private TripleTable tripleTable ;
        private QuadTable quadTable ;
        private DatasetPrefixStorage prefixes ;
        private Location location ;
    }
    
    // StorageRDF
    
    // Hash and/or increments
    // NodeId as > 8 byte.

    // Loader: Try with StreamRDFBatchSplit and a parallel index update.
    //   Needs multi-threaded transaction control.
    //   Mantis -> exclusive mode.  MRSW.
    // Bulk loader from zero:
    //  load SPO, then parallel load POS, OSP, PSO etc.
    
    // ** NodeTableCache and aborts.
    // NodeTableCache + abort -> clean out?
    // Abort notification.
    // Or NodeTableCache part of the transaction.

    // DatasetGraph.exec(op)
    //   Interface ExecuteOp + generic registration.
    // DatasetGraph.getBaseDatasetGraph
    
    // ++ DatasetGraphTriplesQuads

    // really simple - quads/triples only - no 3 or 4 way forms.
    // Or vice versa - 
}