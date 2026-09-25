package org.divyaprabandham.reader;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** OS-managed, network-gated retry; never does network work on the UI thread. */
public final class CorrectionRetryJob extends JobService {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(JobParameters params){
        worker.execute(()->{CorrectionSubmitter.Outcome result=CorrectionSubmitter.submitPending(this);
            jobFinished(params,result.queued>0);});return true;
    }
    @Override public boolean onStopJob(JobParameters params){return true;}
    @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
