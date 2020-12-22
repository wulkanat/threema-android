package ch.threema.app.listeners;

import androidx.annotation.AnyThread;
import ch.threema.storage.models.tasks.TasksModel;

public interface TasksListener {
	@AnyThread void onClosed(final TasksModel tasksModel);
	@AnyThread void onModified(final TasksModel tasksModel);
	@AnyThread void onCreated(final TasksModel tasksModel);
	@AnyThread void onRemoved(final TasksModel tasksModel);

	/**
	 * return true, if the event have to be handled!
	 */
	@AnyThread boolean handle(final TasksModel tasksModel);
}
