package ch.threema.app.services.tasks;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.exceptions.NotAllowedException;
import ch.threema.app.listeners.TasksListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.base.ThreemaException;
import ch.threema.client.MessageTooLongException;
import ch.threema.storage.models.AbstractMessageModel;
import ch.threema.storage.models.ContactModel;
import ch.threema.storage.models.GroupModel;
import ch.threema.storage.models.tasks.TasksModel;

public interface TasksService {

	interface TasksFilter {
		MessageReceiver<?> getReceiver();
		TasksModel.State[] getStates();
		default String createdOrNotVotedByIdentity() {
			return null;
		}
		boolean filter(TasksModel ballotModel);
	}

	TasksModel create(
		ContactModel contactModel,
		String description,
		TasksModel.State state) throws NotAllowedException;

	boolean modifyFinished(TasksModel tasksModel) throws MessageTooLongException;

	boolean viewingTasks(TasksModel tasksModel, boolean view);

	boolean update(TasksModel tasksModel) throws NotAllowedException;
	boolean close(Integer tasksModelId) throws NotAllowedException, MessageTooLongException;
	boolean send(TasksModel tasksModel, ListenerManager.HandleListener<TasksListener> handleListener) throws MessageTooLongException;

	@Nullable
	TasksModel get(int tasksId);
	TasksModel get(String id, String creator);

	List<TasksModel> getBallots(TasksFilter filter) throws NotAllowedException;
	long countBallots(TasksFilter filter);

	boolean belongsToMe(Integer ballotModelId, MessageReceiver<?> messageReceiver) throws NotAllowedException;
	BallotUpdateResult update(BallotCreateInterface createMessage) throws ThreemaException;
	boolean update(BallotModel ballotModel);

	BallotPublishResult publish(MessageReceiver<?> messageReceiver, BallotModel ballotModel,
		AbstractMessageModel abstractMessageModel) throws NotAllowedException, MessageTooLongException;

	BallotPublishResult publish(MessageReceiver<?> messageReceiver,
		BallotModel ballotModel,
		AbstractMessageModel abstractMessageModel,
		String receivingIdentity) throws NotAllowedException, MessageTooLongException;

	LinkBallotModel getLinkedBallotModel(BallotModel ballotModel) throws NotAllowedException;
	boolean remove(BallotModel ballotModel) throws NotAllowedException;
	boolean remove(MessageReceiver<?> receiver);

	/*
	choice stuff
	 */
	List<BallotChoiceModel> getChoices(Integer ballotModelId) throws NotAllowedException;

	/*
	voting stuff
	 */

	BallotVoteResult vote(Integer ballotModelId, Map<Integer, Integer> values) throws NotAllowedException;
	BallotVoteResult vote(BallotVoteInterface ballotVoteMessage) throws NotAllowedException;

	/**
	 * return the count of votings depending on the ballot properties
	 */
	int getVotingCount(BallotChoiceModel choiceModel);
	boolean removeVotes(MessageReceiver<?> receiver, String identity);

	@NonNull List<String> getVotedParticipants(Integer ballotModelId);
	@NonNull List<String> getPendingParticipants(Integer ballotModelId);
	@NonNull String[] getParticipants(Integer ballotModelId);
	@NonNull String[] getParticipants(MessageReceiver<?> messageReceiver);

	boolean hasVoted(Integer ballotModelId, String fromIdentity);

	/**
	 * get my votes
	 */
	List<BallotVoteModel> getMyVotes(Integer ballotModelId) throws NotAllowedException;

	/**
	 * get all votes of a ballot
	 */
	List<BallotVoteModel> getBallotVotes(Integer ballotModelId) throws NotAllowedException;

	MessageReceiver<?> getReceiver(BallotModel ballotModel);

	BallotMatrixData getMatrixData(int ballotModelId);

	boolean removeAll();

	/**
	 * Check if a poll is complete i.e. all voters have cast their vote
	 * @param model BallotModel to check
	 * @return true if all participants voted, false otherwise
	 */
	boolean isComplete(BallotModel model);
}
