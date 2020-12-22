package ch.threema.app.adapters.tasks;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

import ch.threema.app.R;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ballot.BallotService;
import ch.threema.app.ui.AvatarListItemUtil;
import ch.threema.app.ui.CountBoxView;
import ch.threema.app.ui.listitemholder.AvatarListItemHolder;
import ch.threema.app.utils.BallotUtil;
import ch.threema.app.utils.LocaleUtil;
import ch.threema.app.utils.NameUtil;
import ch.threema.app.utils.ViewUtil;
import ch.threema.storage.models.tasks.TasksModel;

public class ConversationTaskListAdapter extends ArrayAdapter<TasksModel>  {

	private Context context;
	private ContactService contactService;
	private List<TasksModel> values;

	public ConversationTaskListAdapter(Context context,
	                                   List<TasksModel> values,
	                                   BallotService ballotService,
	                                   ContactService contactService) {
		super(context, R.layout.item_ballot_overview, values);

		this.context = context;
		this.values = values;
		this.contactService = contactService;
	}

	private static class BallotOverviewItemHolder extends AvatarListItemHolder {
		public TextView name;
		public TextView state;
		public TextView creator;
		public TextView creationDate;
		public CountBoxView countBoxView;
	}

	@NotNull
	@Override
	public View getView(int position, View convertView, @NotNull ViewGroup parent) {
		View itemView = convertView;
		BallotOverviewListAdapter.BallotOverviewItemHolder holder;

		if (convertView == null) {
			holder = new BallotOverviewListAdapter.BallotOverviewItemHolder();
			LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			itemView = inflater.inflate(R.layout.item_ballot_overview, parent, false);

			holder.name= itemView.findViewById(R.id.ballot_name);
			holder.state = itemView.findViewById(R.id.ballot_state);
			holder.creationDate = itemView.findViewById(R.id.ballot_creation_date);
			holder.creator = itemView.findViewById(R.id.ballot_creator);
			holder.countBoxView = itemView.findViewById(R.id.ballot_updates);
			holder.avatarView = itemView.findViewById(R.id.avatar_view);

			itemView.setTag(holder);
		}
		else {
			holder = (BallotOverviewListAdapter.BallotOverviewItemHolder) itemView.getTag();
		}
		final BallotModel ballotModel = values.get(position);

		if(ballotModel != null) {
			final ContactModel contactModel = this.contactService.getByIdentity(ballotModel.getCreatorIdentity());
			AvatarListItemUtil.loadAvatar(position, contactModel, null, contactService, holder);

			if(holder.name != null) {
				holder.name.setText(ballotModel.getName());
			}

			if (ballotModel.getState() == BallotModel.State.CLOSED) {
				holder.state.setText(R.string.ballot_state_closed);
				holder.state.setVisibility(View.VISIBLE);
			} else if (ballotModel.getState() == BallotModel.State.OPEN) {
				if (BallotUtil.canClose(ballotModel, contactService.getMe().getIdentity())
					|| BallotUtil.canViewMatrix(ballotModel, contactService.getMe().getIdentity())) {
					holder.state.setText(String.format(Locale.US, "%d / %d",
						ballotService.getVotedParticipants(ballotModel.getId()).size(),
						ballotService.getParticipants(ballotModel.getId()).length));
				} else {
					holder.state.setText(R.string.ballot_secret);
				}
				holder.state.setVisibility(View.VISIBLE);
			} else {
				holder.state.setText("");
				holder.state.setVisibility(View.GONE);
			}

			ViewUtil.show(holder.countBoxView, false);

			if(holder.creationDate != null) {
				holder.creationDate.setText(LocaleUtil.formatTimeStampString(this.getContext(), ballotModel.getCreatedAt().getTime(), true));
			}

			if(holder.creator != null) {
				holder.creator.setText(NameUtil.getDisplayName(this.contactService.getByIdentity(ballotModel.getCreatorIdentity())));
			}
		}

		return itemView;
	}
}
