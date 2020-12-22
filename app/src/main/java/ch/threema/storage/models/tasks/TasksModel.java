package ch.threema.storage.models.tasks;

import java.util.Date;

public class TasksModel {
	public static final String TABLE = "tasks";
	public static final String COLUMN_ID = "id";
	public static final String COLUMN_API_TASKS_ID = "apiTasksId";
	public static final String COLUMN_CREATOR_IDENTITY = "creatorIdentity";
	public static final String COLUMN_NAME= "name";
	public static final String COLUMN_STATE= "state";
	public static final String COLUMN_CREATED_AT= "createdAt";
	public static final String COLUMN_MODIFIED_AT= "modifiedAt";
	public static final String COLUMN_LAST_VIEWED_AT= "lastViewedAt";

	public enum State {
		PENDING, DONE
	}

	private int id;
	private String apiTasksId;
	private String creatorIdentity;
	private String name;
	private State state;
	private Date createdAt;
	private Date modifiedAt;
	private Date lastViewedAt;

	public int getId() {
		return id;
	}

	public TasksModel setId(int id) {
		this.id = id;
		return this;
	}

	public String getApiTasksId() {
		return apiTasksId;
	}

	public TasksModel setApiTasksId(String apiTasksId) {
		this.apiTasksId = apiTasksId;
		return this;
	}

	public String getCreatorIdentity() {
		return creatorIdentity;
	}

	public TasksModel setCreatorIdentity(String creatorIdentity) {
		this.creatorIdentity = creatorIdentity;
		return this;
	}

	public String getName() {
		return name;
	}

	public TasksModel setName(String name) {
		this.name = name;
		return this;
	}

	public TasksModel.State getState() {
		return state;
	}

	public TasksModel setState(State state) {
		this.state = state;
		return this;
	}

	public Date getCreatedAt() {
		return createdAt;
	}

	public TasksModel setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Date getModifiedAt() {
		return modifiedAt;
	}

	public TasksModel setModifiedAt(Date modifiedAt) {
		this.modifiedAt = modifiedAt;
		return this;
	}

	public Date getLastViewedAt() {
		return this.lastViewedAt;
	}

	public TasksModel setLastViewedAt(Date lastViewedAt) {
		this.lastViewedAt = lastViewedAt;
		return this;
	}

}
