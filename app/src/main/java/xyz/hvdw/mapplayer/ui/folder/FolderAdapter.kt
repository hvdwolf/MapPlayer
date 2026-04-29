package xyz.hvdw.mapplayer.ui.folder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.data.MusicRepository
import xyz.hvdw.mapplayer.model.FolderItem

class FolderAdapter(
    private val folders: List<FolderItem>,
    private val listener: FolderClickListener
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    interface FolderClickListener {
        fun onFolderClick(folder: FolderItem)
        fun onFolderLongClick(view: View, folder: FolderItem)
        fun onFolderPlay(folder: FolderItem, shuffle: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun getItemCount(): Int = folders.size

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folders[position], listener)
    }

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val row: View = itemView.findViewById(R.id.folderRow)
        private val image: ImageView = itemView.findViewById(R.id.folderImage)
        private val name: TextView = itemView.findViewById(R.id.folderName)
        private val subtitle: TextView = itemView.findViewById(R.id.folderSubtitle)
        private val menu: ImageView = itemView.findViewById(R.id.folderMenu)

        fun bind(folder: FolderItem, listener: FolderClickListener) {
            val context = itemView.context

            // Folder name
            name.text = folder.name

            // Cached track count (instant)
            val count = MusicRepository.getTrackCount(context, folder.uri)
            subtitle.text = itemView.context.getString(R.string.folder_subtitle, count)

            // Folder thumbnail (cover.jpg → embedded art → fallback)
            val thumb: Bitmap? = MusicRepository.getFolderThumbnail(context, folder.uri)
            if (thumb != null) {
                image.setImageBitmap(thumb)
            } else {
                //image.setImageResource(R.drawable.ic_folder_music)
                val bitmap = BitmapFactory.decodeResource(context.resources, R.raw.ic_launcher_blue)
                image.setImageBitmap(bitmap)
            }

            // Row click
            row.setOnClickListener {
                listener.onFolderClick(folder)
            }

            // Long press
            row.setOnLongClickListener {
                listener.onFolderLongClick(row, folder)
                true
            }

            // Three-dot menu
            menu.setOnClickListener {
                val popup = PopupMenu(context, menu)
                popup.menuInflater.inflate(R.menu.menu_folder, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_play -> {
                            listener.onFolderPlay(folder, false)
                            true
                        }
                        R.id.action_play_random -> {
                            listener.onFolderPlay(folder, true)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }
}
