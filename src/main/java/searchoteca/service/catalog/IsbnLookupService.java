package searchoteca.service.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import searchoteca.DTO.archive.IsbnLookupResult;
import searchoteca.components.Isbn;
import searchoteca.exception.ExternalServiceException;
import searchoteca.exception.ResourceConflictException;
import searchoteca.exception.ResourceNotFoundException;
import searchoteca.repository.catalog.BookRepository;

import java.util.Optional;

/**
 * Busca os dados de um livro pelo ISBN para preencher o cadastro (RF05 / RNF09). Nada é salvo aqui.
 *
 * Ordem:
 *  1. ISBNdb (principal, com chave). Se achar o livro mas faltar algum campo (autor, ano, editora,
 *     gênero), a Open Library completa só o que faltou.
 *  2. Open Library, se a ISBNdb não conhecer o ISBN, estiver fora do ar ou sem chave configurada.
 *
 * Respostas: 200 com os dados | 404 nenhuma das duas conhece o ISBN | 409 ISBN inválido ou já cadastrado
 *            | 503 as fontes consultadas estão fora do ar. Em 404 e 503 o operador cadastra manualmente.
 */
@Service
public class IsbnLookupService {

    private static final Logger log = LoggerFactory.getLogger(IsbnLookupService.class);

    private final IsbnDbClient isbnDb;
    private final OpenLibraryClient openLibrary;
    private final BookRepository bookRepository;

    public IsbnLookupService(IsbnDbClient isbnDb, OpenLibraryClient openLibrary, BookRepository bookRepository) {
        this.isbnDb = isbnDb;
        this.openLibrary = openLibrary;
        this.bookRepository = bookRepository;
    }

    public IsbnLookupResult lookup(String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        if (!Isbn.isValid(isbn)) {
            throw new ResourceConflictException("ISBN inválido. Confira os números (ISBN-10 ou ISBN-13).");
        }
        if (bookRepository.findByIsbn(isbn) != null) {
            throw new ResourceConflictException("Este ISBN já está cadastrado no acervo.");
        }

        boolean anySourceAnswered = false;

        // 1. ISBNdb
        if (isbnDb.enabled()) {
            try {
                Optional<IsbnLookupResult> found = isbnDb.find(isbn);
                anySourceAnswered = true;
                if (found.isPresent()) {
                    return completeWithOpenLibrary(found.get());
                }
            } catch (ExternalServiceException e) {
                log.info("ISBN {}: ISBNdb indisponível, tentando a Open Library.", isbn);
            }
        }

        // 2. Open Library
        try {
            Optional<IsbnLookupResult> found = openLibrary.find(isbn);
            anySourceAnswered = true;
            if (found.isPresent()) {
                return found.get();
            }
        } catch (ExternalServiceException e) {
            log.info("ISBN {}: Open Library indisponível.", isbn);
        }

        if (!anySourceAnswered) {
            throw new ExternalServiceException(
                    "Não foi possível consultar as bases de livros agora. Preencha os dados do livro manualmente.");
        }
        throw new ResourceNotFoundException(
                "ISBN não encontrado nas bases de livros. Preencha os dados do livro manualmente.");
    }

    /** A ISBNdb achou o livro; se faltou algum campo, tenta completar pela Open Library (sem atrapalhar se ela falhar). */
    private IsbnLookupResult completeWithOpenLibrary(IsbnLookupResult main) {
        boolean complete = main.author() != null && main.rel_year() != null
                && main.publisher() != null && main.genre() != null;
        if (complete) {
            return main;
        }
        try {
            Optional<IsbnLookupResult> extra = openLibrary.find(main.isbn());
            if (extra.isEmpty()) {
                return main;
            }
            IsbnLookupResult o = extra.get();
            boolean used = (main.author() == null && o.author() != null)
                    || (main.rel_year() == null && o.rel_year() != null)
                    || (main.publisher() == null && o.publisher() != null)
                    || (main.genre() == null && o.genre() != null);
            return new IsbnLookupResult(
                    main.isbn(),
                    main.title(),
                    main.author() != null ? main.author() : o.author(),
                    main.rel_year() != null ? main.rel_year() : o.rel_year(),
                    main.publisher() != null ? main.publisher() : o.publisher(),
                    main.genre() != null ? main.genre() : o.genre(),
                    used ? IsbnDbClient.SOURCE + " + " + OpenLibraryClient.SOURCE : main.source());
        } catch (ExternalServiceException e) {
            return main;
        }
    }
}
