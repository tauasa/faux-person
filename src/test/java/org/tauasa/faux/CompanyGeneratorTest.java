package org.tauasa.faux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tauasa.faux.model.Company;
import org.tauasa.faux.model.Person;

class CompanyGeneratorTest {

    private static final DataSet DATA = DataSet.get();

    @Test
    void sameSeedGivesSameCompanies() {
        assertEquals(CompanyGenerator.builder().seed(42).build().next(1000),
                CompanyGenerator.builder().seed(42).build().next(1000));
        assertNotEquals(CompanyGenerator.builder().seed(1).build().next(20),
                CompanyGenerator.builder().seed(2).build().next(20));
        CompanyGenerator random = CompanyGenerator.builder().build();
        List<Company> first = random.next(5);
        assertEquals(first, CompanyGenerator.builder().seed(random.seed()).build().next(5));
    }

    @Test
    void byDefaultCompaniesAreMarked() {
        Set<Company> marked = DATA.companies.stream()
                .map(c -> new Company(c.name() + " [faux]", c.city(), c.state(),
                        CompanyGenerator.exampleDomain(c.domain())))
                .collect(Collectors.toSet());
        for (Company c : CompanyGenerator.builder().seed(3).build().next(2000)) {
            assertTrue(marked.contains(c), c.toString());
        }
    }

    @Test
    void realCompaniesChangesOnlyTheMarks() {
        Set<Company> real = Set.copyOf(DATA.companies);
        List<Company> asIs = CompanyGenerator.builder().seed(7).realCompanies(true).build().next(500);
        List<Company> marked = CompanyGenerator.builder().seed(7).build().next(500);
        for (int i = 0; i < asIs.size(); i++) {
            Company r = asIs.get(i);
            assertTrue(real.contains(r), r.toString());
            assertEquals(new Company(r.name() + " [faux]", r.city(), r.state(),
                    CompanyGenerator.exampleDomain(r.domain())), marked.get(i));
        }
        assertTrue(real.contains(CompanyGenerator.builder().seed(7).realCompanies(true).build().split().next()));
    }

    @Test
    void drawsTheWayPersonGeneratorDrawsAnEmployer() {
        // The same draw for both, so a custom company file affects both alike.
        for (long seed = 0; seed < 20; seed++) {
            Person p = PersonGenerator.builder().seed(seed).build().next();
            assertTrue(p.employer().name().endsWith(" [faux]"), p.employer().toString());
        }
        assertEquals("costco.example", CompanyGenerator.exampleDomain("costco.com"));
        assertEquals("localhost.example", CompanyGenerator.exampleDomain("localhost"));
    }

    @Test
    void splitIsReproducibleAndDiffersFromTheParent() {
        CompanyGenerator a = CompanyGenerator.builder().seed(9).build();
        CompanyGenerator b = CompanyGenerator.builder().seed(9).build();
        CompanyGenerator sa = a.split();
        assertEquals(sa.next(50), b.split().next(50));
        assertNotEquals(sa.next(50), a.next(50));
    }

    @Test
    void dataDirectoryReplacesTheCompanyFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("us_companies_domains.txt"), "Acme Widgets|Springfield|IL|acme.test\n");
        for (Company c : CompanyGenerator.builder().seed(4).dataDirectory(dir).build().next(20)) {
            assertEquals(new Company("Acme Widgets [faux]", "Springfield", "IL", "acme.example"), c);
        }
    }

    @Test
    void countsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> CompanyGenerator.builder().build().next(-1));
        assertEquals(25, CompanyGenerator.generateListOfRandomCompanies(25).size());
        assertEquals(10, CompanyGenerator.builder().build().stream().limit(10).count());
    }
}
