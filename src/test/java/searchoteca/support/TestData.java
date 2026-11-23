package searchoteca.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import searchoteca.model.Books.BookModel;
import searchoteca.model.Books.CopyModel;
import searchoteca.model.DepartmentModel;
import searchoteca.model.LocationModel;
import searchoteca.model.RoleModel;
import searchoteca.model.StudentModel;
import searchoteca.model.UserModel;
import searchoteca.security.CustomUserDetails;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Objetos de exemplo e atalhos de login usados pelos testes. */
public final class TestData {

    private TestData() {}

    public static UserModel user(long id, String username, String role) {
        UserModel u = new UserModel();
        u.setUserId(id);
        u.setUsername(username);
        u.setCompleteName("Nome " + username);
        u.setEmail(username + "@escola.com");
        u.setPassword(FakePasswordEncoder.PREFIX + "senhaAtual1");
        u.setRole_code(role);
        u.setStatus(true);
        u.setMust_change_password(false);
        return u;
    }

    public static CustomUserDetails details(UserModel user, String... permissions) {
        return new CustomUserDetails(user, new ArrayList<>(Arrays.asList(permissions)), false);
    }

    public static CustomUserDetails operator() {
        return details(user(1L, "operador", "loan_op"), "loans:create");
    }

    public static RoleModel role(String code, boolean root) {
        RoleModel r = new RoleModel();
        r.setRoleCode(code);
        r.setRoot(root);
        return r;
    }

    public static StudentModel student(long studentId, long userId, String ra) {
        StudentModel s = new StudentModel();
        s.setStudentId(studentId);
        s.setUserId(userId);
        s.setRa(ra);
        s.setPhone("11999990000");
        return s;
    }

    public static BookModel book(String isbn, String title) {
        BookModel b = new BookModel();
        b.setIsbn(isbn);
        b.setTitle(title);
        b.setAuthor("Autor de " + title);
        return b;
    }

    public static BookModel book(String isbn, String title, String price) {
        BookModel b = book(isbn, title);
        b.setSalePrice(price == null ? null : new BigDecimal(price));
        return b;
    }

    public static CopyModel copy(long id, String isbn, int index, String localCode, String status) {
        CopyModel c = new CopyModel();
        c.setCopyId(id);
        c.setIsbn(isbn);
        c.setCopy_index(index);
        c.setLocalCode(localCode);
        c.setDepartCode("DEP");
        c.setStatus(status);
        c.setCopy_custom_code(isbn + index);   // igual ao banco: ISBN + número do exemplar
        return c;
    }

    public static LocationModel location(String code, String depart) {
        LocationModel l = new LocationModel();
        l.setLocalCode(code);
        l.setLocalName("Estante " + code);
        l.setLocalDesc("Descrição " + code);
        l.setDepartCode(depart);
        return l;
    }

    public static DepartmentModel department(String code) {
        DepartmentModel d = new DepartmentModel();
        d.setDepartCode(code);
        d.setDepartName("Departamento " + code);
        d.setDepartDesc("Descrição " + code);
        return d;
    }

    /** Coloca o usuário no SecurityContext, como se tivesse passado pelo filtro do JWT. */
    public static void loginAs(CustomUserDetails user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    /** Login só com as permissões informadas (ex.: "ROLE_ROOT", "account_requests:manage"). */
    public static void loginWithAuthorities(String... authorities) {
        List<SimpleGrantedAuthority> list = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("usuario.teste", null, list));
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
