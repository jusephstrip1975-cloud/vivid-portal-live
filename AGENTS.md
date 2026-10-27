<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->

- Keep reference-based animated backgrounds in a dedicated catalog and gallery, reusing the existing Android wallpaper bridge; this isolates new media from unrelated catalog and native behavior.
- Gate background actions through a pure capability policy, and distinguish picker opening from system application; this prevents unsupported-platform success claims.
- Publish the public wallpaper catalog through /api/public/catalog with absolute media URLs; installed Capacitor apps refresh it at startup, resume and on demand, retaining a validated offline cache so new media does not require repackaging the APK.
