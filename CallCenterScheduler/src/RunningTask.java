/**
 * Snapshot of an assignment currently executing on a worker.
 *
 * <p>Stored in the {@code runningQueue} min-heap, ordered by
 * {@code finishTime} so the earliest-completing task is always polled first.
 */
public class RunningTask {

    public final Assignment assignment;
    public final long       finishTime;

    public RunningTask(Assignment assignment, long finishTime) {
        this.assignment = assignment;
        this.finishTime = finishTime;
    }

    @Override
    public String toString() {
        return assignment.key() + " @finish=" + finishTime;
    }
}
