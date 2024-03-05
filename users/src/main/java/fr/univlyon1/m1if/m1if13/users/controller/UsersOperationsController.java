package fr.univlyon1.m1if.m1if13.users.controller;

import fr.univlyon1.m1if.m1if13.users.dao.UserDao;
import fr.univlyon1.m1if.m1if13.users.model.User;
import org.apache.coyote.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;


import javax.naming.AuthenticationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static fr.univlyon1.m1if.m1if13.users.utils.JwtHelper.generateToken;
import static fr.univlyon1.m1if.m1if13.users.utils.JwtHelper.verifyToken;
import static fr.univlyon1.m1if.m1if13.users.utils.JwtHelper.noLifeTimeToken;

/**
 * Controller des opérations sur users.
 */
@Controller
public class UsersOperationsController {

    @Autowired
    private UserDao userDao;

    /**
     * Procédure de login utilisée par un utilisateur. JSON
     * @param requestParams map avec tout les paramètres
     * @return Une ResponseEntity avec le JWT dans le header "Authentication" si le
     * login s'est bien passé, et le code de statut approprié (204, 401 ou 404).
     */
    @ResponseBody
    @PostMapping(value = "/login", consumes = {MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "To let a user connect",
            tags = "Operation controller",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            })
    public ResponseEntity<Void> loginJson(@RequestBody final Map<String, Object> requestParams,
                                          @RequestHeader("Origin") final String origin)
            throws AuthenticationException, BadRequestException {
        String login = (String) requestParams.get("login");
        String password = (String) requestParams.get("password");
        if (login == null || password == null) {
            throw new BadRequestException("Il manque un paramètre");
        }
        Optional<User> user = userDao.get(login);
        if (user.isPresent()) {
            user.get().authenticate(password);
            if (user.get().isConnected()) {
                String token = generateToken(login, origin);
                HttpHeaders headers = new HttpHeaders();
                headers.add("Authentication", "Bearer " + token);
                return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
            } else {
                return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
            }
        } else {
            throw new NoSuchElementException("l'utilisateur " + login + " n'existe pas");
        }
    }

    /**
     * Procédure de login utilisée par un utilisateur. URL-encoded
     * @param login Le login de l'utilisateur. L'utilisateur doit avoir été créé
     *             préalablement et son login  private UserDao userDao; doit
     *              être présent dans le DAO.
     * @param password Le password à vérifier.
     * @return Une ResponseEntity avec le JWT dans le header "Authentication" si le
     * login s'est bien passé, et le code de statut approprié (204, 401 ou 404).
     */
    @PostMapping(value = "/login", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    @Operation(summary = "To let a user connect",
            tags = "Operation controller",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            })
    public ResponseEntity<Void> loginUrlEncoded(@RequestParam(value = "login", required = false)
                                                    final String login,
                                      @RequestParam(value = "password", required = false)
                                      final String password,
                                      @RequestHeader("Origin") final String origin)
            throws AuthenticationException, BadRequestException {
        if (login == null || password == null) {
            throw new BadRequestException("Il manque un paramètre");
        }
        Optional<User> user = userDao.get(login);
        if (user.isPresent()) {
            user.get().authenticate(password);
            if (user.get().isConnected()) {
                String token = generateToken(login, origin);
                HttpHeaders headers = new HttpHeaders();
                headers.add("Authentication", "Bearer " + token);
                return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
            } else {
                throw new AuthenticationException("L'utilisateur n'est pas autorisé");
            }
        } else {
            throw new NoSuchElementException("l'utilisateur " + login + " n'existe pas");
        }
    }

    /**
     * Réalise la déconnexion.
     * @param jwt Le token JWT qui se trouve dans le header "Authentication" de la requête
     * @param origin L'origine de la requête (pour la comparer avec celle du client,
     * stockée dans le token JWT)
     *@return Une réponse vide avec un code de statut approprié (204, 400, 401).
     */
    @PostMapping("/logout")
    @Operation(summary = "To let a user disconnect",
            tags = "Operation controller",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<Void> logout(@RequestHeader("Authentication") final String jwt,
                                       @RequestHeader("origin") final String origin)
            throws AuthenticationException, BadRequestException {
        String token = jwt.replace("Bearer ", "");
        String login = verifyToken(token, origin);
        Optional<User> user = userDao.get(login);
        if (user.isPresent()) {
            if (user.get().isConnected()) {
                user.get().disconnect();
                String newToken = noLifeTimeToken(login, origin);
                HttpHeaders headers = new HttpHeaders();
                headers.add("Authentication", "Bearer " + newToken);
                return new ResponseEntity<>(headers, HttpStatus.NO_CONTENT);
            } else {
                throw new BadRequestException("L'utilisateur ne devrais "
                        + "pas pouvoir se déconnecter");
            }
        } else {
            throw new AuthenticationException("L'utilisateur n'est pas autorisé");
        }
    }


    /**
     * Méthode destinée au serveur Node pour valider l'authentification d'un utilisateur.
     * @param jwt Le token JWT qui se trouve dans le header "Authentication" de la requête
     * @param origin L'origine de la requête (pour la comparer avec celle du client,
     *               stockée dans le token JWT)
     * @return Une réponse vide avec un code de statut approprié (204, 400, 401).
     */
    @GetMapping("/authenticate")
    @Operation(summary = "To let a user authentificate",
            tags = "Operation controller",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content"),
                    @ApiResponse(responseCode = "400", description = "Bad request"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            })
    public ResponseEntity<Void> authenticate(@RequestParam("jwt") final String jwt,
                                             @RequestParam("origin") final String origin)
            throws AuthenticationException, BadRequestException {
        String token = jwt.replace("Bearer ", "");
        String login = verifyToken(token, origin);
        Optional<User> user = userDao.get(login);
        if (user.isPresent()) {
            if (user.get().isConnected()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            } else {
                throw new BadRequestException("L'utilisateur devrais être connecté");
            }
        } else {
            throw new AuthenticationException("le token à expiré");
        }
    }
}
