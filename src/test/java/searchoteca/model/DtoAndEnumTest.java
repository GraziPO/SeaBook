package searchoteca.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import searchoteca.DTO.archive.publicSearch.PageResponse;
import searchoteca.DTO.dashboard.DashboardKpis;
import searchoteca.DTO.dashboard.DepartmentOccupation;
import searchoteca.DTO.users.UserResponse;
import searchoteca.model.Books.status.CopyStatus;
import searchoteca.model.Books.status.ExitType;
import searchoteca.model.settings.SettingKey;
import searchoteca.support.TestData;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regras que moram nos próprios DTOs e enums. */
class DtoAndEnumTest {

    @Test
    @DisplayName("cada tipo de saída leva o exemplar à situação certa")
    void exitTypeResultingStatus() {
        assertEquals(CopyStatus.LOANED, ExitType.LOAN.resultingStatus());
        assertEquals(CopyStatus.SOLD, ExitType.SALE.resultingStatus());
        assertEquals(CopyStatus.DONATED, ExitType.DONATION.resultingStatus());
        assertEquals(CopyStatus.WRITTEN_OFF, ExitType.WRITE_OFF.resultingStatus());
    }

    @Test
    @DisplayName("SettingKey: chave do banco, tipo e limites")
    void settingKeys() {
        assertEquals(SettingKey.LOAN_PERIOD_DAYS, SettingKey.fromKey("loan.period-days"));
        assertNull(SettingKey.fromKey("chave.antiga"));
        assertEquals(SettingKey.Type.BOOLEAN, SettingKey.SALES_ENABLED.type());
        assertEquals(1, SettingKey.LOAN_PERIOD_DAYS.min());
        assertEquals(90, SettingKey.LOAN_PERIOD_DAYS.max());
        assertEquals("students.inactivity-days", SettingKey.INACTIVITY_DAYS.key());
        assertFalse(SettingKey.WAITLIST_MAX_SIZE.label().isBlank());
    }

    @Test
    @DisplayName("ocupação do departamento em %, sem dividir por zero")
    void departmentOccupation() {
        assertEquals(25, new DepartmentOccupation("CIE", 1, 4).percentual());
        assertEquals(0, new DepartmentOccupation("CIE", 0, 0).percentual());
        assertEquals(67, new DepartmentOccupation("CIE", 2, 3).percentual());
    }

    @Test
    @DisplayName("movimentações no período = empréstimos + devoluções + saídas")
    void movements() {
        DashboardKpis k = new DashboardKpis(0, 0, 0, 0, 0, 0, 0, 3, 2, 1, BigDecimal.ZERO, 0);
        assertEquals(6, k.movementsInPeriod());
    }

    @Test
    @DisplayName("PageResponse.from copia os dados da página do Spring")
    void pageResponseFrom() {
        PageResponse<String> p = PageResponse.from(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));
        assertEquals(List.of("a", "b"), p.content());
        assertEquals(1, p.page());
        assertEquals(2, p.size());
        assertEquals(5, p.totalElements());
        assertEquals(3, p.totalPages());
    }

    @Test
    @DisplayName("UserResponse nunca expõe a senha")
    void userResponse() {
        UserResponse r = UserResponse.from(TestData.user(1L, "maria", "warehouse_op"));
        assertEquals("maria", r.username());
        assertEquals("warehouse_op", r.roleCode());
        assertTrue(r.status());
    }
}
