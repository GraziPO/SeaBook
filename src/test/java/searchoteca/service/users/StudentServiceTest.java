package searchoteca.service.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import searchoteca.DTO.users.student.StudentRequest;
import searchoteca.exception.ResourceConflictException;
import searchoteca.model.StudentModel;
import searchoteca.model.UserModel;
import searchoteca.repository.users.StudentRepository;
import searchoteca.repository.users.UserRepository;
import searchoteca.support.FakePasswordEncoder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Cadastro de aluno sob demanda: a senha inicial é o RA e a troca é obrigatória no primeiro acesso. */
class StudentServiceTest {

    private StudentRepository studentRepository;
    private UserRepository userRepository;
    private StudentService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        userRepository = mock(UserRepository.class);
        service = new StudentService(studentRepository, new FakePasswordEncoder(), userRepository);
        when(userRepository.save(any())).thenAnswer(i -> {
            UserModel u = i.getArgument(0);
            u.setUserId(50L);
            return u;
        });
        when(studentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("cadastro: perfil students, senha = RA criptografado, troca obrigatória")
    void createOk() {
        StudentModel s = service.create(new StudentRequest("maria", "Maria Souza", "maria@escola.com", null, "RA123", "11999990000"));

        ArgumentCaptor<UserModel> user = ArgumentCaptor.forClass(UserModel.class);
        verify(userRepository).save(user.capture());
        assertEquals("students", user.getValue().getRole_code());
        assertEquals("hash:RA123", user.getValue().getPassword());
        assertTrue(user.getValue().getMust_change_password());
        assertTrue(user.getValue().getStatus());

        assertEquals(50L, s.getUserId());
        assertEquals("RA123", s.getRa());
        assertEquals(LocalDate.now(), s.getLastActivity());
    }

    @Test
    @DisplayName("RA vazio ou ausente: recusado sem gravar nada")
    void raRequired() {
        assertThrows(ResourceConflictException.class,
                () -> service.create(new StudentRequest("maria", "Maria", "m@x.com", null, " ", null)));
        assertThrows(ResourceConflictException.class,
                () -> service.create(new StudentRequest("maria", "Maria", "m@x.com", null, null, null)));
        verifyNoInteractions(userRepository, studentRepository);
    }

    @Test
    @DisplayName("cadastro de aluno: e-mail gravado em minúsculas")
    void createLowercasesEmail() {
        service.create(new StudentRequest("maria", "Maria Souza", " Maria.Souza@Escola.COM ", null, "RA123", null));
        ArgumentCaptor<UserModel> user = ArgumentCaptor.forClass(UserModel.class);
        verify(userRepository).save(user.capture());
        assertEquals("maria.souza@escola.com", user.getValue().getEmail());
    }

    @Test
    @DisplayName("cadastro de aluno: e-mail já usado (em qualquer caixa) é recusado sem gravar")
    void createDuplicatedEmail() {
        when(userRepository.findByEmail("maria@escola.com")).thenReturn(new UserModel());
        assertThrows(ResourceConflictException.class,
                () -> service.create(new StudentRequest("maria2", "Maria", "MARIA@escola.com", null, "RA9", null)));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(studentRepository);
    }
}
