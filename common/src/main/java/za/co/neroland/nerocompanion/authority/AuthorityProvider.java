package za.co.neroland.nerocompanion.authority;

/**
 * An extra authority source: claim and access systems of sibling mods, registered by their
 * compat facades only when that sibling is loaded (Stage 6). A provider can only <b>deny</b>; returning
 * {@link Decision#ALLOW} means "no objection", never a grant beyond vanilla. A provider that throws (including
 * {@code LinkageError}) is treated as a denial: the chain fails closed.
 */
@FunctionalInterface
public interface AuthorityProvider {

    Decision check(AuthorityQuery query);
}
