package org.elasticsearch.index.engine;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LatLonDocValuesField;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.monitor.jvm.JvmInfo;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@SuppressWarnings("unused") //invoked by benchmarking framework
public class InternalEngineBenchmark {
    private static final Path INDEX_PATH = Paths.get(System.getProperty("data.dir", "/tmp/lucene-7-bench-index"));

    public Directory dir;
    public IndexWriter writer;

    @Param({"100"})
    public int tokens;

    // as we write to the file system (and each iteration takes a longer time than usual) we want to reset the state per iteration.
    @Setup(Level.Iteration)
    public void setUp() throws Exception {
        // always ensure we start with an empty index directory
        if (System.getProperty("data.dir.cleanup") != null) {
            IOUtils.rm(INDEX_PATH);
        }
        // indexing buffer size = 50%, 8 shards -> 1/16th of heap size. Note that we'll likely bound by perThreadHardLimitMB anyway...
        double indexingBuffer = Double.valueOf(
            System.getProperty("buffer", String.valueOf(JvmInfo.jvmInfo().getConfiguredMaxHeapSize() / 16)));

        dir = FSDirectory.open(INDEX_PATH);
        writer = new IndexWriter(dir, new IndexWriterConfig()
            .setRAMBufferSizeMB(indexingBuffer)
            .setOpenMode(OpenMode.CREATE));
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws IOException {
        IOUtils.close(writer, dir);
        writer = null;
        dir = null;
    }

    @State(Scope.Thread)
    public static class DocumentTemplate {
        private final String prefix = UUID.randomUUID().toString();

        private int counter;

        public String nextId() {
            counter++;
            return prefix + counter;
        }

        public long seqNo() {
            return counter;
        }

        long totalAmount() {
            return counter * 31 % 1000;
        }

        long improvementSurcharge() {
            return counter * 31 % 101;
        }

        long fareAmount() {
            return counter * 31 % 11;
        }

        double latitude() {
            return -74 + ((counter * 31) % 1755) / 1000d;
        }

        double longitude() {
            return 40 + ((counter * 31) % 1237) / 500d;
        }

        long date() {
            return (47L * 365 * 24 * 60 * 60 * 1000) + (counter * 31) % 100000;
        }

        long tripDistance() {
            return (counter * 31) % 837;
        }
    }


    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class EventCounters {
        public int docs;
    }



    @CompilerControl(CompilerControl.Mode.INLINE)
    public Document indexDoc(DocumentTemplate template) throws Exception {
        Document doc = new Document();

        // simulate the _id field
        doc.add(new StringField("_id", template.nextId(), Store.NO));

        // simulate the seq no
        doc.add(new NumericDocValuesField("_seq_no", template.seqNo()));

        // total_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "total_amount", template.totalAmount());

        // improvement_surcharge: scaled_float with scaling factor of 100
        addLongValue(doc, "improvement_surcharge", template.improvementSurcharge());

        // pickup_location: geopoint
        addGeoPointValue(doc, "pickup_location", template.latitude(), template.longitude());

        // pickup_datetime: date
        addLongValue(doc, "pickup_datetime", template.date());

        // trip_type: keyword
        addKeywordValue(doc, "trip_type", "1");

        // dropoff_datetime: date
        addLongValue(doc, "dropoff_datetime", template.date());

        // trip_type: keyword
        addKeywordValue(doc, "rate_code_id", "1");

        // tolls_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "tolls_amount", 0);

        // dropoff_location: geopoint
        addGeoPointValue(doc, "dropoff_location", template.latitude(), template.longitude());

        // passenger_count: long
        addLongValue(doc, "passenger_count", 1);

        // fare_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "fare_amount", template.fareAmount());

        // extra: scaled_float with scaling factor of 100
        addLongValue(doc, "extra", template.fareAmount());

        // trip_distance: scaled_float with scaling factor of 100
        addLongValue(doc, "trip_distance", template.tripDistance());

        // tip_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "tip_amount", 0);

        // store_and_fwd_flag: keyword
        addKeywordValue(doc, "store_and_fwd_flag", "N");

        // payment_type: keyword
        addKeywordValue(doc, "payment_type", "2");

        // trip_distance: scaled_float with scaling factor of 100
        addLongValue(doc, "mta_tax", 50);

        // vendor_id: keyword
        addKeywordValue(doc, "vendor_id", "2");

        // simulate the _field_names field
        doc.add(new StringField("_field_names", "_id", Store.NO));
        doc.add(new StringField("_field_names", "_seq_no", Store.NO));
        doc.add(new StringField("_field_names", "_source", Store.NO));
        doc.add(new StringField("_field_names", "total_amount", Store.NO));
        doc.add(new StringField("_field_names", "improvement_surcharge", Store.NO));
        doc.add(new StringField("_field_names", "pickup_location", Store.NO));
        doc.add(new StringField("_field_names", "pickup_datetime", Store.NO));
        doc.add(new StringField("_field_names", "trip_type", Store.NO));
        doc.add(new StringField("_field_names", "dropoff_datetime", Store.NO));
        doc.add(new StringField("_field_names", "rate_code_id", Store.NO));
        doc.add(new StringField("_field_names", "dropoff_location", Store.NO));
        doc.add(new StringField("_field_names", "passenger_count", Store.NO));
        doc.add(new StringField("_field_names", "fare_amount", Store.NO));
        doc.add(new StringField("_field_names", "extra", Store.NO));
        doc.add(new StringField("_field_names", "trip_distance", Store.NO));
        doc.add(new StringField("_field_names", "tip_amount", Store.NO));
        doc.add(new StringField("_field_names", "store_and_fwd_flag", Store.NO));
        doc.add(new StringField("_field_names", "payment_type", Store.NO));
        doc.add(new StringField("_field_names", "mta_tax", Store.NO));
        doc.add(new StringField("_field_names", "vendor_id", Store.NO));

        // TODO: _source?
        // TODO: randomize content more?

        writer.addDocument(doc);

        return doc;
    }

    private void addLongValue(Document doc, String field, long value) {
        doc.add(new LongPoint(field, value));
        doc.add(new SortedNumericDocValuesField(field, value));
    }

    private void addKeywordValue(Document doc, String field, String value) {
        BytesRef binaryValue = new BytesRef(value);
        doc.add(new StringField(field, binaryValue, Store.NO));
        doc.add(new SortedSetDocValuesField(field, binaryValue));
    }

    private void addGeoPointValue(Document doc, String field, double lat, double lon) {
        doc.add(new LatLonPoint(field, lat, lon));
        doc.add(new LatLonDocValuesField(field, lat, lon));
    }


    @Benchmark
    @Group("index_1")
    @GroupThreads()
    public Document indexDoc_1(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }

    @Benchmark
    @Group("index_2")
    @GroupThreads(2)
    public Document indexDoc_2(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }

    @Benchmark
    @Group("index_4")
    @GroupThreads(4)
    public Document indexDoc_4(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }

    @Benchmark
    @Group("index_8")
    @GroupThreads(8)
    public Document indexDoc_8(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }

    @Benchmark
    @Group("index_16")
    @GroupThreads(16)
    public Document indexDoc_16(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }

    @Benchmark
    @Group("index_32")
    @GroupThreads(32)
    public Document indexDoc_32(DocumentTemplate template, Blackhole bh, EventCounters events) throws Exception {
        bh.consume(tokens);
        events.docs++;
        return indexDoc(template);
    }
}
