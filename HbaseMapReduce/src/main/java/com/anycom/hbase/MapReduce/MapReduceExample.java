package main.java.com.anycom.hbase.MapReduce;

import main.java.com.anycom.hbase.MapReduce.ExampleSetup;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

import java.io.IOException;
import java.util.Calendar;

public class MapReduceExample
{

    public MapReduceExample() {

    }

    static class MyMapper extends TableMapper<LongWritable, LongWritable>
    {
        private LongWritable ONE = new LongWritable( 1 );

        public MyMapper() {
        }

        @Override
        protected void map( ImmutableBytesWritable rowkey, Result columns, Context context ) throws IOException, InterruptedException

        {

            // Get the timestamp from the row key
            long timestamp = ExampleSetup.getTimestampFromRowKey(rowkey.get());

            // Get hour of day
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis( timestamp );
            int hourOfDay = calendar.get( Calendar.HOUR_OF_DAY );

            // Output the current hour of day and a count of 1
            context.write( new LongWritable( hourOfDay ), ONE );
        }
    }

    static class MyReducer extends Reducer<LongWritable, LongWritable, LongWritable, LongWritable>

    {
        public MyReducer() {
        }

        @Override
        protected void reduce(LongWritable key, Iterable<LongWritable> values, Context context) throws IOException, InterruptedException

        {
            // Add up all of the page views for this hour
            long sum = 0;
            for( LongWritable count : values )
            {
                sum += count.get();
            }

            // Write out the current hour and the sum
            context.write( key, new LongWritable( sum ) );
        }
    }

    public static void main( String[] args )
    {
        try
        {
            // Setup Hadoop
            Configuration conf = HBaseConfiguration.create();
            Job job = Job.getInstance(conf, "PageViewCounts");
            job.setJarByClass( MapReduceExample.class );

            // Create a scan
            Scan scan = new Scan();

            // Configure the Map process to use HBase
            TableMapReduceUtil.initTableMapperJob(

                    "PageViews",                    // The name of the table
                    scan,                           // The scan to execute against the table
                    MyMapper.class,                 // The Mapper class
                    LongWritable.class,             // The Mapper output key class
                    LongWritable.class,             // The Mapper output value class
                    job );                          // The Hadoop job

            // Configure the reducer process
            job.setReducerClass( MyReducer.class );
            job.setCombinerClass( MyReducer.class );

            // Setup the output - we'll write to the file system: HOUR_OF_DAY   PAGE_VIEW_COUNT
            job.setOutputKeyClass( LongWritable.class );
            job.setOutputValueClass( LongWritable.class );
            job.setOutputFormatClass( TextOutputFormat.class );

            // We'll run just one reduce task, but we could run multiple
            job.setNumReduceTasks( 1 );

            // Write the results to a file in the output directory
            FileOutputFormat.setOutputPath( job, new Path( "output" ) );

            // Execute the job
            System.exit( job.waitForCompletion( true ) ? 0 : 1 );

        }
        catch( Exception e )
        {
            e.printStackTrace();
        }
    }
}