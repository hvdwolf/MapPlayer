package xyz.hvdw.mapplayer.ui.folder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import xyz.hvdw.mapplayer.R
import xyz.hvdw.mapplayer.model.FolderItem

class FolderGalleryAdapter(
    private val folders: List<FolderItem>,
    private val listener: FolderClickListener
) : RecyclerView.Adapter<FolderGalleryAdapter.FolderViewHolder>() {

    interface FolderClickListener {
        fun onOpen(folder: FolderItem)
        fun onLongPress(view: View, folder: FolderItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_gallery, parent, false)
        return FolderViewHolder(view)
    }

    override fun getItemCount(): Int = folders.size

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(folders[position], listener)
    }

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val thumb: ImageView = itemView.findViewById(R.id.folderThumb)
        private val name: TextView = itemView.findViewById(R.id.folderName)
        //private val btnPlay: ImageButton = itemView.findViewById(R.id.btnPlay)
        //private val btnShuffle: ImageButton = itemView.findViewById(R.id.btnShuffle)

        fun bind(folder: FolderItem, listener: FolderClickListener) {
            name.text = folder.name

            // Thumbnail or fallback icon
            if (folder.thumbnail != null) {
                thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                thumb.setImageBitmap(folder.thumbnail)
            } else {
                thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
                thumb.setImageResource(R.drawable.ic_folder_music)
            }

            // Single tap → open folder
            itemView.setOnClickListener {
                listener.onOpen(folder)
            }

            // Long press → show popup menu (same as list view)
            itemView.setOnLongClickListener {
                listener.onLongPress(itemView, folder)
                true
            }
        }


    }
}
