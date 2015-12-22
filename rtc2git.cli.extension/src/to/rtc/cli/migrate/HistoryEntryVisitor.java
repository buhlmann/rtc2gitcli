package to.rtc.cli.migrate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogBaselineEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogChangeSetEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogComponentEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogDirectionEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogOslcLinkEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogVersionableEntryDTO;
import com.ibm.team.filesystem.common.internal.rest.client.changelog.ChangeLogWorkItemEntryDTO;
import com.ibm.team.filesystem.rcp.core.internal.changelog.BaseChangeLogEntryVisitor;
import com.ibm.team.filesystem.rcp.core.internal.changelog.IChangeLogOutput;

public class HistoryEntryVisitor extends BaseChangeLogEntryVisitor {

	private final List<RtcTag> tags;
	private String component;
	private RtcTag actualTag;
	private final Map<String, String> lastChangeSets;
	private boolean lastChangeSetReached;

	public HistoryEntryVisitor(IChangeLogOutput out, Map<String, String> lastChangeSets) {
		tags = new ArrayList<RtcTag>();
		setOutput(out);
		this.lastChangeSets = lastChangeSets;
		this.lastChangeSetReached = false;
	}

	public List<RtcTag> acceptInto(ChangeLogEntryDTO root) {
		if (!enter(root)) {
			return tags;
		}
		for (Iterator<?> iterator = root.getChildEntries().iterator(); iterator.hasNext();) {
			ChangeLogEntryDTO child = (ChangeLogEntryDTO) iterator.next();
			visitChild(root, child);
			acceptInto(child);
		}

		exit(root);
		return tags;
	}

	@Override
	protected void visitChangeSet(ChangeLogEntryDTO parent, ChangeLogChangeSetEntryDTO changeSetDto) {
		if (lastChangeSetReached) {
			return;
		}
		String changeSetUuid = changeSetDto.getItemId();
		RtcChangeSet changeSet = new RtcChangeSet(changeSetUuid).setText(changeSetDto.getEntryName())
				.setCreatorName(changeSetDto.getCreator().getFullName())
				.setCreatorEMail(changeSetDto.getCreator().getEmailAddress())
				.setCreationDate(changeSetDto.getCreationDate()).setComponent(component);
		@SuppressWarnings("unchecked")
		List<ChangeLogWorkItemEntryDTO> workItems = changeSetDto.getWorkItems();
		if (workItems != null && !workItems.isEmpty()) {
			for (ChangeLogWorkItemEntryDTO workItem : workItems) {
				changeSet.addWorkItem(workItem.getWorkItemNumber(), workItem.getEntryName());
			}
		}
		if (actualTag == null || !actualTag.getName().equals(parent.getEntryName())) {
			actualTag = getTag("HEAD");
		}
		addToActualTag(changeSet);
		if (lastChangeSets.get(component).equals(changeSetUuid)) {
			lastChangeSetReached = true;
		}
	}

	private void addToActualTag(RtcChangeSet changeset) {
		actualTag.add(changeset);
	}

	@Override
	protected void visitBaseline(ChangeLogEntryDTO parent, ChangeLogBaselineEntryDTO dto) {
		if (lastChangeSetReached) {
			return;
		}
		actualTag = getTag(dto);
	}

	@Override
	protected void visitComponent(ChangeLogEntryDTO parent, ChangeLogComponentEntryDTO dto) {
		component = dto.getEntryName();
		lastChangeSetReached = false;
	}

	@Override
	protected void visitDirection(ChangeLogEntryDTO parent, ChangeLogDirectionEntryDTO dto) {
	}

	@Override
	protected void visitOslcLink(ChangeLogEntryDTO parent, ChangeLogOslcLinkEntryDTO dto) {
	}

	@Override
	protected void visitVersionable(ChangeLogEntryDTO parent, ChangeLogVersionableEntryDTO dto) {
	}

	@Override
	protected void visitWorkItem(ChangeLogEntryDTO parent, ChangeLogWorkItemEntryDTO dto, boolean inChangeSet) {
	}

	private RtcTag getTag(ChangeLogBaselineEntryDTO dto) {
		for (RtcTag tag : tags) {
			if (dto.getEntryName().equals(tag.getName())) {
				if (tag.getCreationDate() > dto.getCreationDate()) {
					tag.setCreationDate(dto.getCreationDate());
				}
				return tag;
			}
		}
		RtcTag tag = new RtcTag(dto.getItemId()).setCreationDate(dto.getCreationDate()).setName(dto.getEntryName());
		tags.add(tag);
		return tag;
	}

	private RtcTag getTag(String name) {
		for (RtcTag tag : tags) {
			if (name.equals(tag.getName())) {
				return tag;
			}
		}
		RtcTag tag = new RtcTag(null).setName(name).setCreationDate(Long.MAX_VALUE);
		tags.add(tag);
		return tag;
	}

}
