package org.divyaprabandham.reader;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class ContentUpdateWorker extends Worker {
    public ContentUpdateWorker(@NonNull Context context,@NonNull WorkerParameters parameters){super(context,parameters);}
    @NonNull @Override public Result doWork(){try{
        String result=new ContentUpdates(getApplicationContext()).check();
        return "unconfigured".equals(result)?Result.success():Result.success();
    }catch(Exception ex){android.util.Log.w("ContentUpdates","Content check deferred: "+ex.getClass().getSimpleName());return Result.retry();}}
}
