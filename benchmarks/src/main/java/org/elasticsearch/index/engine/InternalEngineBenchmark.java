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
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@SuppressWarnings("unused") //invoked by benchmarking framework
public class InternalEngineBenchmark {

    private static final Path INDEX_PATH = Paths.get("/tmp/lucene-7-bench-index");

    Directory dir;
    IndexWriter writer;

    @Setup
    public void setUp() throws Exception {
        dir = FSDirectory.open(INDEX_PATH);
        writer = new IndexWriter(dir, new IndexWriterConfig()
            // indexing buffer size = 50%, 8 shards -> 1/16th of heap size. Note that we'll likely bound by perThreadHardLimitMB anyway...
            .setRAMBufferSizeMB(JvmInfo.jvmInfo().getConfiguredMaxHeapSize() / 16)
            .setOpenMode(OpenMode.CREATE));
    }

    @TearDown
    public void teadDown() throws IOException {
        IOUtils.close(writer, dir);
        writer = null;
        dir = null;
    }

    // You can use this idiom to store thread local data. JMH will create one instance per thread and inject it to your benchmark method.
    @State(Scope.Thread)
    public static class Ids {
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

    public Object indexDoc(Ids ids) throws Exception {
        Document doc = new Document();

        // simulate the _id field
        doc.add(new StringField("_id", ids.nextId(), Store.NO));

        // simulate the seq no
        doc.add(new NumericDocValuesField("_seq_no", ids.seqNo()));

        // total_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "total_amount", ids.totalAmount());

        // improvement_surcharge: scaled_float with scaling factor of 100
        addLongValue(doc, "improvement_surcharge", ids.improvementSurcharge());

        // pickup_location: geopoint
        addGeoPointValue(doc, "pickup_location", ids.latitude(), ids.longitude());

        // pickup_datetime: date
        addLongValue(doc, "pickup_datetime", ids.date());

        // trip_type: keyword
        addKeywordValue(doc, "trip_type", "1");

        // dropoff_datetime: date
        addLongValue(doc, "dropoff_datetime", ids.date());

        // trip_type: keyword
        addKeywordValue(doc, "rate_code_id", "1");

        // tolls_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "tolls_amount", 0);

        // dropoff_location: geopoint
        addGeoPointValue(doc, "dropoff_location", ids.latitude(), ids.longitude());

        // passenger_count: long
        addLongValue(doc, "passenger_count", 1);

        // fare_amount: scaled_float with scaling factor of 100
        addLongValue(doc, "fare_amount", ids.fareAmount());

        // extra: scaled_float with scaling factor of 100
        addLongValue(doc, "extra", ids.fareAmount());

        // trip_distance: scaled_float with scaling factor of 100
        addLongValue(doc, "trip_distance", ids.tripDistance());

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

    // These are three example benchmark methods. They will index documents with 1, 2 and 4 threads

    @Benchmark
    @Group("index_1")
    @GroupThreads()
    public Object indexDoc_1(Ids ids) throws Exception {
        return indexDoc(ids);
    }

    @Benchmark
    @Group("index_2")
    @GroupThreads(2)
    public Object indexDoc_2(Ids ids) throws Exception {
        return indexDoc(ids);
    }

    @Benchmark
    @Group("index_4")
    @GroupThreads(4)
    public Object indexDoc_4(Ids ids) throws Exception {
        return indexDoc(ids);
    }
}
