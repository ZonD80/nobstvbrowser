package com.aengix.tvbrowser

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aengix.tvbrowser.databinding.DialogEnterUrlBinding
import com.aengix.tvbrowser.databinding.FragmentBookmarksBinding
import com.aengix.tvbrowser.databinding.ItemBookmarkBinding

class BookmarksFragment : Fragment() {

    interface Listener {
        fun onOpenUrl(url: String)
        fun onOpenSettings()
    }

    private var listener: Listener? = null
    private var _binding: FragmentBookmarksBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookmarkStore: BookmarkStore
    private lateinit var adapter: BookmarkAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookmarksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bookmarkStore = (requireActivity() as MainActivity).bookmarkStore()
        adapter = BookmarkAdapter(
            onOpen = { bookmark -> listener?.onOpenUrl(bookmark.url) },
            onEdit = { bookmark -> showEditDialog(bookmark) }
        )

        binding.recyclerBookmarks.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerBookmarks.adapter = adapter
        binding.recyclerBookmarks.setHasFixedSize(true)
        PointerScrollHelper.attach(binding.recyclerBookmarks)

        binding.buttonEnterUrl.setOnClickListener { showEnterUrlDialog() }
        binding.buttonSettings.setOnClickListener { listener?.onOpenSettings() }
        BrandingFooter.bind(binding.brandingFooter.textBrandingFooter) { url ->
            listener?.onOpenUrl(url)
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val bookmarks = bookmarkStore.getAll()
        adapter.submit(bookmarks)
        binding.textEmpty.visibility = if (bookmarks.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerBookmarks.visibility = if (bookmarks.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showEnterUrlDialog() {
        EnterUrlDialogFragment { url ->
            listener?.onOpenUrl(url)
        }.show(parentFragmentManager, "enter_url")
    }

    private fun showEditDialog(bookmark: Bookmark) {
        BookmarkEditDialogFragment(bookmark) {
            refreshList()
        }.show(parentFragmentManager, "edit_bookmark")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): BookmarksFragment = BookmarksFragment()
    }

    private class BookmarkAdapter(
        private val onOpen: (Bookmark) -> Unit,
        private val onEdit: (Bookmark) -> Unit
    ) : RecyclerView.Adapter<BookmarkAdapter.ViewHolder>() {

        private var items: List<Bookmark> = emptyList()

        fun submit(bookmarks: List<Bookmark>) {
            items = bookmarks
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBookmarkBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: ItemBookmarkBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(bookmark: Bookmark) {
                binding.textTitle.text = bookmark.title
                binding.textUrl.text = bookmark.url
                val open = { onOpen(bookmark) }
                binding.bookmarkBody.setOnClickListener { open() }
                binding.buttonOpen.setOnClickListener { open() }
                binding.buttonEdit.setOnClickListener { onEdit(bookmark) }
            }
        }
    }
}

class EnterUrlDialogFragment(
    private val onOpen: (String) -> Unit
) : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = DialogEnterUrlBinding.inflate(inflater, container, false)

        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonOpen.setOnClickListener {
            val normalized = UrlUtils.normalize(binding.editUrl.text?.toString().orEmpty())
            if (normalized == null) {
                Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onOpen(normalized)
            dismiss()
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.6).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}

class BookmarkEditDialogFragment(
    private val bookmark: Bookmark,
    private val onChanged: () -> Unit
) : DialogFragment() {

    companion object {
        private const val REQUEST_DELETE_CONFIRM = "delete_bookmark_confirm"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.setFragmentResultListener(
            REQUEST_DELETE_CONFIRM,
            viewLifecycleOwner
        ) { _, bundle ->
            if (bundle.getBoolean(ConfirmDialogFragment.RESULT_CONFIRMED)) {
                val store = (requireActivity() as MainActivity).bookmarkStore()
                store.delete(bookmark.id)
                Toast.makeText(requireContext(), R.string.bookmark_deleted, Toast.LENGTH_SHORT).show()
                onChanged()
                dismiss()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = com.aengix.tvbrowser.databinding.DialogBookmarkEditBinding
            .inflate(inflater, container, false)
        val store = (requireActivity() as MainActivity).bookmarkStore()

        binding.editName.setText(bookmark.title)
        binding.editUrl.setText(bookmark.url)

        binding.buttonSave.setOnClickListener {
            val title = binding.editName.text?.toString()?.trim().orEmpty()
            val url = UrlUtils.normalize(binding.editUrl.text?.toString().orEmpty())
            if (title.isEmpty() || url == null) {
                Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updated = bookmark.copy(title = title, url = url)
            if (store.getAll().any { it.id == bookmark.id }) {
                store.update(updated)
            } else {
                store.add(updated)
            }
            Toast.makeText(requireContext(), R.string.bookmark_saved, Toast.LENGTH_SHORT).show()
            onChanged()
            dismiss()
        }

        binding.buttonDelete.setOnClickListener {
            ConfirmDialogFragment.show(
                this,
                REQUEST_DELETE_CONFIRM,
                getString(R.string.delete_bookmark_confirm)
            )
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.6).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
