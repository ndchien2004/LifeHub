package com.lifehub.application.task;

import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.TaskRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tag management (FR-PRJ-04, FR-TSK-06). */
@Service
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final TaskRepository taskRepository;

    public TagService(TagRepository tagRepository, TaskRepository taskRepository) {
        this.tagRepository = tagRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    /** How many live tasks use each tag, keyed by tag id. Absent means zero. */
    @Transactional(readOnly = true)
    public Map<String, Long> usageCounts() {
        return taskRepository.countUsageByTag();
    }

    public Tag create(String name, String color) {
        requireNameAvailable(name, null);
        return tagRepository.save(new Tag(name, color));
    }

    public Tag update(String id, String name, String color) {
        Tag tag = require(id);
        if (name != null) {
            requireNameAvailable(name, id);
            tag.rename(name);
        }
        if (color != null) {
            tag.recolor(color);
        }
        return tagRepository.save(tag);
    }

    /**
     * Hard deletes a tag (item C-5b).
     *
     * <p>The single approved exception to the soft delete rule. A tag is a label rather than
     * user data, and the join table cascade detaches it from every task and, from Phase 3,
     * every transaction.
     */
    public void delete(String id) {
        tagRepository.delete(require(id));
    }

    private Tag require(String id) {
        return tagRepository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy nhãn"));
    }

    private void requireNameAvailable(String name, String excludingId) {
        if (name != null && tagRepository.existsByName(name.trim(), excludingId)) {
            throw new ConflictException("Tên nhãn đã tồn tại", "name");
        }
    }
}
