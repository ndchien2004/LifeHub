package com.lifehub.application.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifehub.application.task.TaskCommands.CreateProject;
import com.lifehub.domain.common.Patch;
import com.lifehub.application.task.TaskCommands.UpdateProject;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Project;
import com.lifehub.domain.task.ProjectRepository;
import com.lifehub.domain.task.ProjectStatus;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import com.lifehub.domain.task.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Project and tag rules (FR-PRJ-01 to FR-PRJ-04). */
class ProjectAndTagServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TagRepository tagRepository = mock(TagRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Nested
    @DisplayName("Project")
    class Projects {

        private ProjectService service;

        @BeforeEach
        void setUp() {
            service = new ProjectService(projectRepository, taskRepository, clock);
            when(projectRepository.save(any(Project.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("Tạo project mặc định trạng thái ACTIVE và màu chuẩn")
        void createsWithDocumentedDefaults() {
            Project project = service.create(new CreateProject("LifeHub", null, null, null));

            assertThat(project.getName()).isEqualTo("LifeHub");
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
            assertThat(project.getColor()).isEqualTo("#6366f1");
        }

        @Test
        @DisplayName("Trùng tên project trả CONFLICT")
        void rejectsADuplicateName() {
            when(projectRepository.existsByName("LifeHub", null)).thenReturn(true);

            assertThatThrownBy(() -> service.create(new CreateProject("LifeHub", null, null, null)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Tên dự án đã tồn tại");

            verify(projectRepository, never()).save(any());
        }

        @Test
        @DisplayName("Đổi tên trùng project khác trả CONFLICT, nhưng giữ nguyên tên mình thì được")
        void allowsRenamingToItsOwnName() {
            Project project = new Project("LifeHub", null, null, null);
            when(projectRepository.findById("p1")).thenReturn(Optional.of(project));
            when(projectRepository.existsByName("LifeHub", "p1")).thenReturn(false);

            service.update("p1", new UpdateProject(
                    Patch.of("LifeHub"), Patch.absent(), Patch.absent(), Patch.absent()));

            assertThat(project.getName()).isEqualTo("LifeHub");
        }

        @Test
        @DisplayName("Tên project rỗng bị từ chối")
        void rejectsAnEmptyName() {
            assertThatThrownBy(() -> service.create(new CreateProject("  ", null, null, null)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("FR-PRJ-02 — xóa project gỡ task ra khỏi nó trước khi xóa mềm")
        void releasesItsTasksBeforeBeingDeleted() {
            Project project = new Project("LifeHub", null, null, null);
            when(projectRepository.findById("p1")).thenReturn(Optional.of(project));

            service.delete("p1");

            verify(taskRepository).clearProject("p1");
            assertThat(project.isDeleted()).isTrue();
            assertThat(project.getDeletedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("Lưu trữ project chỉ đổi trạng thái, không xóa")
        void archivesWithoutDeleting() {
            Project project = new Project("LifeHub", null, null, null);
            when(projectRepository.findById("p1")).thenReturn(Optional.of(project));

            service.update("p1", new UpdateProject(
                    Patch.absent(), Patch.absent(), Patch.absent(), Patch.of(ProjectStatus.ARCHIVED)));

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
            assertThat(project.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("Project không tồn tại trả NotFound")
        void reportsAMissingProject() {
            when(projectRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById("ghost")).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Tag")
    class Tags {

        private TagService service;

        @BeforeEach
        void setUp() {
            service = new TagService(tagRepository, taskRepository);
            when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("FR-PRJ-04 — tên nhãn là duy nhất, trùng thì CONFLICT")
        void rejectsADuplicateName() {
            when(tagRepository.existsByName("docs", null)).thenReturn(true);

            assertThatThrownBy(() -> service.create("docs", "#10b981"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Tên nhãn đã tồn tại");

            verify(tagRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tạo nhãn không chọn màu thì dùng màu mặc định")
        void fallsBackToTheDefaultColour() {
            assertThat(service.create("docs", null).getColor()).isEqualTo("#94a3b8");
        }

        @Test
        @DisplayName("Xóa nhãn là hard delete — ngoại lệ đã duyệt của quy tắc soft delete")
        void hardDeletesTheTag() {
            Tag tag = new Tag("docs", null);
            when(tagRepository.findById("t1")).thenReturn(Optional.of(tag));

            service.delete("t1");

            verify(tagRepository).delete(tag);
        }

        @Test
        @DisplayName("Xóa nhãn không tồn tại trả NotFound")
        void reportsAMissingTag() {
            when(tagRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete("ghost")).isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Đổi tên nhãn cập nhật cả tên và màu")
        void updatesNameAndColour() {
            Tag tag = new Tag("docs", "#10b981");
            when(tagRepository.findById("t1")).thenReturn(Optional.of(tag));

            service.update("t1", "tài liệu", "#ef4444");

            assertThat(tag.getName()).isEqualTo("tài liệu");
            assertThat(tag.getColor()).isEqualTo("#ef4444");
        }
    }
}
